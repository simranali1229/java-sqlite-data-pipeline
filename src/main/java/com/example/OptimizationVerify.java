package com.example;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class OptimizationVerify {

    public static void main(String[] args) {

        System.setProperty("db.url", "jdbc:sqlite:test_metrics.db");

        String sql = """
            SELECT business_date, product_id,
                   order_count, total_quantity, total_revenue
            FROM daily_product_metrics
            WHERE (business_date = '2099-12-01' AND product_id = 1)
               OR (business_date = '2099-12-02' AND product_id = 2)
            ORDER BY business_date
            """;

        try (Connection connection = DatabaseConnection.connect();
             PreparedStatement ps = connection.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            System.out.println("OPTIMIZATION METRICS VERIFICATION");

            while (rs.next()) {
                System.out.println(
                    "Date: " + rs.getString("business_date")
                    + " | Product: " + rs.getInt("product_id")
                    + " | Orders: " + rs.getInt("order_count")
                    + " | Quantity: " + rs.getInt("total_quantity")
                    + " | Revenue: " + rs.getDouble("total_revenue")
                );
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
