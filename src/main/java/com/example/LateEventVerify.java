package com.example;

import java.sql.*;

public class LateEventVerify {

    public static void main(String[] args) {

        String url = "jdbc:sqlite:test_metrics.db";

        try (Connection con = DriverManager.getConnection(url)) {

            System.out.println("LATE EVENT VERIFICATION");
            System.out.println("-----------------------");

            // Verify the original order's business date.
            String orderSql =
                "SELECT * FROM transaction_state WHERE order_id = ?";

            try (PreparedStatement ps = con.prepareStatement(orderSql)) {

                ps.setLong(1, 2000100002L);

                try (ResultSet rs = ps.executeQuery()) {
                    ResultSetMetaData md = rs.getMetaData();
                    int columns = md.getColumnCount();

                    while (rs.next()) {
                        System.out.println("TRANSACTION STATE:");

                        for (int i = 1; i <= columns; i++) {
                            System.out.println(
                                md.getColumnName(i) + " = " +
                                rs.getString(i)
                            );
                        }
                    }
                }
            }

            // Verify the daily metric for the historical business date.
            String metricSql =
                "SELECT * FROM daily_product_metrics " +
                "WHERE business_date = ? AND product_id = ?";

            try (PreparedStatement ps = con.prepareStatement(metricSql)) {

                ps.setString(1, "2025-01-10");
                ps.setInt(2, 1);

                try (ResultSet rs = ps.executeQuery()) {
                    ResultSetMetaData md = rs.getMetaData();
                    int columns = md.getColumnCount();

                    System.out.println("\nDAILY PRODUCT METRICS:");

                    int count = 0;

                    while (rs.next()) {
                        count++;

                        for (int i = 1; i <= columns; i++) {
                            System.out.println(
                                md.getColumnName(i) + " = " +
                                rs.getString(i)
                            );
                        }
                    }

                    System.out.println("Metric rows found: " + count);
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
