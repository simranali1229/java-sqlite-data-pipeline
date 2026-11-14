package com.example;

import java.sql.Connection;
import java.sql.Statement;
import java.sql.ResultSet;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.util.Locale;

public class App {

    public static void main(String[] args) {

        System.out.println("Starting Data Engineering Pipeline...");

        // STEP 1: Create database tables and migrate existing orders
        DatabaseSetup.createTables();

        // STEP 2: Process incremental transaction events
        try {
            System.out.println("\n========== INCREMENTAL EVENT PROCESSING ==========");

            EventProcessor.processCsv("transaction_events.csv");

            System.out.println("\nIncremental events processed successfully!");

        } catch (Exception e) {
            System.out.println("Error processing transaction events!");
            e.printStackTrace();
            return;
        }

        // STEP 3: Connect to SQLite database
        try (Connection conn = DatabaseConnection.connect()) {

            conn.setAutoCommit(true);

            System.out.println("\nDatabase Connected!");

            // STEP 4: Check Orders Table Columns
            System.out.println("\n========== ORDERS TABLE COLUMNS ==========");

            String schemaQuery = "PRAGMA table_info(orders)";

            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(schemaQuery)) {

                while (rs.next()) {
                    System.out.println(rs.getString("name"));
                }
            }

            // STEP 5: Calculate Total Revenue
            String revenueQuery = """
                SELECT COALESCE(SUM(quantity * unit_price), 0)
                AS total_revenue
                FROM transaction_state
                WHERE is_active = 1
                """;

            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(revenueQuery)) {

                if (rs.next()) {

                    System.out.println("\n========== OVERALL METRICS ==========");

                    System.out.printf(
                        Locale.US,
                        "Total Revenue: %.2f%n",
                        rs.getDouble("total_revenue")
                    );
                }
            }

            // STEP 6: Calculate Total Orders
            String orderQuery = """
                SELECT COUNT(*) AS total_orders
                FROM transaction_state
                WHERE is_active = 1
                """;

            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(orderQuery)) {

                if (rs.next()) {

                    System.out.println(
                        "Total Orders: " +
                        rs.getInt("total_orders")
                    );
                }
            }

            // STEP 7: Calculate Total Units Sold
            String unitsQuery = """
                SELECT COALESCE(SUM(quantity), 0) AS total_units
                FROM transaction_state
                WHERE is_active = 1
                """;

            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(unitsQuery)) {

                if (rs.next()) {
                    System.out.println(
                        "Total Units Sold: " +
                        rs.getInt("total_units")
                    );
                }
            }

            // STEP 8: Find Top Selling Products
            String productQuery = """
                SELECT
                    p.product_name,
                    SUM(t.quantity) AS total_sold
                FROM transaction_state t
                JOIN products p
                    ON t.product_id = p.product_id
                WHERE t.is_active = 1
                GROUP BY p.product_id, p.product_name
                ORDER BY total_sold DESC
                """;

            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(productQuery)) {

                System.out.println(
                    "\n========== TOP SELLING PRODUCTS =========="
                );

                while (rs.next()) {

                    System.out.println(
                        rs.getString("product_name") +
                        " - Sold: " +
                        rs.getInt("total_sold")
                    );
                }
            }

            // STEP 9: Calculate Daily Metrics
            String dailyMetricsQuery = """
                SELECT
                    business_date AS order_day,
                    COUNT(*) AS total_orders,
                    SUM(quantity * unit_price) AS daily_revenue,
                    SUM(quantity) AS total_units_sold
                FROM transaction_state
                WHERE is_active = 1
                GROUP BY business_date
                ORDER BY business_date
                """;

            // STEP 10: Generate CSV Report
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(dailyMetricsQuery);
                 BufferedWriter writer = new BufferedWriter(
                     new FileWriter("daily_metrics_report.csv"))) {

                System.out.println(
                    "\n========== DAILY METRICS REPORT =========="
                );

                // CSV Header
                writer.write(
                    "Date,Total Orders,Daily Revenue,Units Sold"
                );

                writer.newLine();

                System.out.printf(
                    "%-15s %-15s %-18s %-15s%n",
                    "Date",
                    "Total Orders",
                    "Daily Revenue",
                    "Units Sold"
                );

                System.out.println(
                    "--------------------------------------------------------------"
                );

                while (rs.next()) {

                    String date = rs.getString("order_day");

                    int totalOrders = rs.getInt("total_orders");

                    double dailyRevenue =
                        rs.getDouble("daily_revenue");

                    int unitsSold =
                        rs.getInt("total_units_sold");

                    // Display results in CMD
                    System.out.printf(
                        "%-15s %-15d %-18.2f %-15d%n",
                        date,
                        totalOrders,
                        dailyRevenue,
                        unitsSold
                    );

                    // Write results into CSV
                    writer.write(
                        date + "," +
                        totalOrders + "," +
                        String.format(
                            Locale.US,
                            "%.2f",
                            dailyRevenue
                        ) + "," +
                        unitsSold
                    );

                    writer.newLine();
                }
            }

            System.out.println(
                "\nCSV report generated successfully!"
            );

            System.out.println(
                "File: daily_metrics_report.csv"
            );

            System.out.println(
                "\n========== REPORT COMPLETED =========="
            );

        } catch (Exception e) {

            System.out.println(
                "Error executing data pipeline!"
            );

            e.printStackTrace();
        }
    }
}
