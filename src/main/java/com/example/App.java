package com.example;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.sql.ResultSet;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.util.Locale;

public class App {

    public static void main(String[] args) {

        String url = "jdbc:sqlite:product_metrics.db";

        try (Connection conn =
                 DriverManager.getConnection(url)) {

            System.out.println("Database Connected!");

            // STEP 1: Check Orders Table Columns
            System.out.println("\n========== ORDERS TABLE COLUMNS ==========");

            String schemaQuery = "PRAGMA table_info(orders)";

            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(schemaQuery)) {

                while (rs.next()) {
                    System.out.println(rs.getString("name"));
                }
            }

            // STEP 2: Calculate Total Revenue
            String revenueQuery =
                "SELECT SUM(quantity * unit_price) " +
                "AS total_revenue FROM orders";

            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(revenueQuery)) {

                if (rs.next()) {
                    System.out.println("\n========== OVERALL METRICS ==========");
                    System.out.println(
                        "Total Revenue: " +
                        rs.getDouble("total_revenue")
                    );
                }
            }

            // STEP 3: Calculate Total Orders
            String orderQuery =
                "SELECT COUNT(*) AS total_orders FROM orders";

            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(orderQuery)) {

                if (rs.next()) {
                    System.out.println(
                        "Total Orders: " +
                        rs.getInt("total_orders")
                    );
                }
            }

            // STEP 4: Find Top Selling Products
            String productQuery =
                "SELECT p.product_name, " +
                "SUM(o.quantity) AS total_sold " +
                "FROM orders o " +
                "JOIN products p " +
                "ON o.product_id = p.product_id " +
                "GROUP BY p.product_id, p.product_name " +
                "ORDER BY total_sold DESC";

            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(productQuery)) {

                System.out.println("\n========== TOP SELLING PRODUCTS ==========");

                while (rs.next()) {
                    System.out.println(
                        rs.getString("product_name") +
                        " - Sold: " +
                        rs.getInt("total_sold")
                    );
                }
            }

            // STEP 5: Daily Metrics Report
            String dailyMetricsQuery =
                "SELECT " +
                "DATE(order_date) AS order_day, " +
                "COUNT(*) AS total_orders, " +
                "SUM(quantity * unit_price) AS daily_revenue, " +
                "SUM(quantity) AS total_units_sold " +
                "FROM orders " +
                "GROUP BY DATE(order_date) " +
                "ORDER BY order_day";

            // STEP 6: Generate CSV Report
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(dailyMetricsQuery);
                 BufferedWriter writer = new BufferedWriter(
                     new FileWriter("daily_metrics_report.csv"))) {

                System.out.println("\n========== DAILY METRICS REPORT ==========");

                // CSV Header
                writer.write("Date,Total Orders,Daily Revenue,Units Sold");
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
                    double dailyRevenue = rs.getDouble("daily_revenue");
                    int unitsSold = rs.getInt("total_units_sold");

                    // Display in CMD
                    System.out.printf(
                        "%-15s %-15d %-18.2f %-15d%n",
                        date,
                        totalOrders,
                        dailyRevenue,
                        unitsSold
                    );

                    // Write data into CSV
                    writer.write(
                        date + "," +
                        totalOrders + "," +
                        String.format(Locale.US, "%.2f", dailyRevenue) + "," +
                        unitsSold
                    );

                    writer.newLine();
                }
            }

            System.out.println("\nCSV report generated successfully!");
            System.out.println("File: daily_metrics_report.csv");

            System.out.println("\n========== REPORT COMPLETED ==========");

        } catch (Exception e) {
            System.out.println("Error executing data pipeline!");
            e.printStackTrace();
        }
    }
}
