package com.example;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

public class DbCheck {

    public static void main(String[] args) {

        try {
            System.setProperty(
                "db.url",
                "jdbc:sqlite:test_metrics.db"
            );

            try (Connection conn = DatabaseConnection.connect();
                 Statement stmt = conn.createStatement()) {

                System.out.println("DATABASE CONNECTED!");

                String[] queries = {
                    "SELECT MAX(order_id) + 1 AS next_order_id FROM transaction_state",
                    "SELECT customer_id FROM customers LIMIT 5",
                    "SELECT product_id FROM products LIMIT 5"
                };

                for (String query : queries) {
                    System.out.println("\nQuery: " + query);

                    try (ResultSet rs = stmt.executeQuery(query)) {
                        int columns = rs.getMetaData().getColumnCount();

                        while (rs.next()) {
                            for (int i = 1; i <= columns; i++) {
                                System.out.print(
                                    rs.getMetaData().getColumnName(i)
                                    + " = " + rs.getString(i) + "  "
                                );
                            }
                            System.out.println();
                        }
                    }
                }
            }

        } catch (Exception e) {
            System.err.println("DATABASE CHECK FAILED!");
            e.printStackTrace();
        }
    }
}
