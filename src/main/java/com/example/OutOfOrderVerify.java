package com.example;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class OutOfOrderVerify {

    public static void main(String[] args) throws Exception {

        String url = "jdbc:sqlite:test_metrics.db";

        try (Connection conn = DriverManager.getConnection(url)) {

            System.out.println("OUT-OF-ORDER EVENT VERIFICATION");
            System.out.println("--------------------------------");

            String sql = """
                SELECT order_id, product_id, quantity, unit_price,
                       business_date, latest_version, is_active
                FROM transaction_state
                WHERE order_id = 2000100003
                """;

            try (PreparedStatement ps = conn.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {

                if (rs.next()) {
                    System.out.println("Order ID: " + rs.getLong("order_id"));
                    System.out.println("Product ID: " + rs.getInt("product_id"));
                    System.out.println("Quantity: " + rs.getInt("quantity"));
                    System.out.println("Unit Price: " + rs.getDouble("unit_price"));
                    System.out.println("Business Date: " + rs.getString("business_date"));
                    System.out.println("Latest Version: " + rs.getInt("latest_version"));
                    System.out.println("Active: " + rs.getInt("is_active"));
                } else {
                    System.out.println("Order not found!");
                }
            }

            System.out.println("\nDAILY PRODUCT METRICS");
            System.out.println("--------------------------------");

            String metricsSql = """
                SELECT business_date, product_id, order_count,
                       total_quantity, total_revenue
                FROM daily_product_metrics
                WHERE business_date IN (
                    '2025-01-10', '2025-01-11', '2025-01-12'
                )
                AND product_id IN (1, 2)
                ORDER BY business_date, product_id
                """;

            try (PreparedStatement ps = conn.prepareStatement(metricsSql);
                 ResultSet rs = ps.executeQuery()) {

                while (rs.next()) {
                    System.out.println(
                        "Date: " + rs.getString("business_date")
                        + " | Product: " + rs.getInt("product_id")
                        + " | Orders: " + rs.getInt("order_count")
                        + " | Quantity: " + rs.getInt("total_quantity")
                        + " | Revenue: " + rs.getDouble("total_revenue")
                    );
                }
            }
        }
    }
}