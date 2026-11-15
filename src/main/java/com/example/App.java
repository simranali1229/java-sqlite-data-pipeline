package com.example;

import java.sql.Connection;
import java.sql.Statement;
import java.sql.ResultSet;
import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

public class App {

    private static final String REPORT_FILE =
            "daily_metrics_report.csv";

    public static void main(String[] args) {

        String inputFile = args.length > 0
                ? args[0]
                : "transaction_events.csv";

        System.out.println(
                "Starting Data Engineering Pipeline..."
        );

        System.out.println("Input file: " + inputFile);

        try {

            // STEP 1: Initialize database tables
            System.out.println(
                    "\n========== DATABASE INITIALIZATION =========="
            );

            DatabaseSetup.createTables();

            // STEP 2: Insert sample reference data
            DataSeeder.insertSampleData();

            // STEP 3: Migrate seeded orders and refresh metrics
            DatabaseSetup.createTables();

            // STEP 4: Process incremental transaction events
            System.out.println(
                    "\n========== INCREMENTAL EVENT PROCESSING =========="
            );

            EventProcessor.processCsv(inputFile);

            System.out.println(
                    "\nIncremental events processed!"
            );

            // STEP 5: Connect to SQLite database
            try (Connection conn = DatabaseConnection.connect()) {

                System.out.println("\nDatabase Connected!");

                // STEP 6: Display orders table columns
                System.out.println(
                        "\n========== ORDERS TABLE COLUMNS =========="
                );

                String schemaQuery =
                        "PRAGMA table_info(orders)";

                try (Statement stmt = conn.createStatement();
                     ResultSet rs = stmt.executeQuery(schemaQuery)) {

                    while (rs.next()) {
                        System.out.println(
                                rs.getString("name")
                        );
                    }
                }

                // STEP 7: Calculate total revenue
                String revenueQuery = """
                        SELECT COALESCE(
                            SUM(quantity * unit_price), 0
                        ) AS total_revenue
                        FROM transaction_state
                        WHERE is_active = 1
                        """;

                try (Statement stmt = conn.createStatement();
                     ResultSet rs = stmt.executeQuery(revenueQuery)) {

                    if (rs.next()) {

                        System.out.println(
                                "\n========== OVERALL METRICS =========="
                        );

                        System.out.printf(
                                Locale.US,
                                "Total Revenue: %.2f%n",
                                rs.getDouble("total_revenue")
                        );
                    }
                }

                // STEP 8: Calculate total active orders
                String orderQuery = """
                        SELECT COUNT(*) AS total_orders
                        FROM transaction_state
                        WHERE is_active = 1
                        """;

                try (Statement stmt = conn.createStatement();
                     ResultSet rs = stmt.executeQuery(orderQuery)) {

                    if (rs.next()) {

                        System.out.println(
                                "Total Orders: "
                                + rs.getInt("total_orders")
                        );
                    }
                }

                // STEP 9: Calculate total units sold
                String unitsQuery = """
                        SELECT COALESCE(
                            SUM(quantity), 0
                        ) AS total_units
                        FROM transaction_state
                        WHERE is_active = 1
                        """;

                try (Statement stmt = conn.createStatement();
                     ResultSet rs = stmt.executeQuery(unitsQuery)) {

                    if (rs.next()) {

                        System.out.println(
                                "Total Units Sold: "
                                + rs.getInt("total_units")
                        );
                    }
                }

                // STEP 10: Find top-selling products
                String productQuery = """
                        SELECT
                            p.product_name,
                            SUM(t.quantity) AS total_sold
                        FROM transaction_state t
                        JOIN products p
                            ON t.product_id = p.product_id
                        WHERE t.is_active = 1
                        GROUP BY
                            p.product_id,
                            p.product_name
                        ORDER BY total_sold DESC
                        """;

                try (Statement stmt = conn.createStatement();
                     ResultSet rs = stmt.executeQuery(productQuery)) {

                    System.out.println(
                            "\n========== TOP SELLING PRODUCTS =========="
                    );

                    while (rs.next()) {

                        System.out.println(
                                rs.getString("product_name")
                                + " - Sold: "
                                + rs.getInt("total_sold")
                        );
                    }
                }

                // STEP 11: Read daily product metrics
                String dailyMetricsQuery = """
                        SELECT
                            business_date AS order_day,
                            SUM(order_count) AS total_orders,
                            SUM(total_revenue) AS daily_revenue,
                            SUM(total_quantity) AS total_units_sold
                        FROM daily_product_metrics
                        GROUP BY business_date
                        ORDER BY business_date
                        """;

                // STEP 12: Generate daily metrics CSV report
                try (Statement stmt = conn.createStatement();
                     ResultSet rs = stmt.executeQuery(dailyMetricsQuery);
                     BufferedWriter writer = Files.newBufferedWriter(
                             Path.of(REPORT_FILE),
                             StandardCharsets.UTF_8)) {

                    System.out.println(
                            "\n========== DAILY METRICS REPORT =========="
                    );

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

                        String date =
                                rs.getString("order_day");

                        int totalOrders =
                                rs.getInt("total_orders");

                        double dailyRevenue =
                                rs.getDouble("daily_revenue");

                        int unitsSold =
                                rs.getInt("total_units_sold");

                        System.out.printf(
                                Locale.US,
                                "%-15s %-15d %-18.2f %-15d%n",
                                date,
                                totalOrders,
                                dailyRevenue,
                                unitsSold
                        );

                        writer.write(
                                date + ","
                                + totalOrders + ","
                                + String.format(
                                        Locale.US,
                                        "%.2f",
                                        dailyRevenue
                                ) + ","
                                + unitsSold
                        );

                        writer.newLine();
                    }
                }

                System.out.println(
                        "\nCSV report generated successfully!"
                );

                System.out.println(
                        "File: " + REPORT_FILE
                );

                System.out.println(
                        "\n========== REPORT COMPLETED =========="
                );
            }

        } catch (Exception e) {

            System.err.println(
                    "\nDATA ENGINEERING PIPELINE FAILED!"
            );

            e.printStackTrace();

            // Return a failure status to the operating system
            System.exit(1);
        }
    }
}
