package com.example;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class PreparedOptimizationVerify {

    public static void main(String[] args) {

        if (args.length != 1) {
            System.out.println(
                "Usage: PreparedOptimizationVerify <database-file>"
            );
            return;
        }

        System.setProperty("db.url", "jdbc:sqlite:" + args[0]);

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

            System.out.println("PREPARED OPTIMIZATION VERIFICATION");
            System.out.println("Database: " + args[0]);

            boolean found = false;

            while (rs.next()) {
                found = true;

                System.out.println(
                    "Date: " + rs.getString("business_date")
                    + " | Product: " + rs.getInt("product_id")
                    + " | Orders: " + rs.getInt("order_count")
                    + " | Quantity: " + rs.getInt("total_quantity")
                    + " | Revenue: " + rs.getDouble("total_revenue")
                );
            }

            if (!found) {
                System.out.println(
                    "No daily metric rows found for the test order."
                );
                System.out.println(
                    "Expected after REVERSE: no active metric contribution."
                );
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
