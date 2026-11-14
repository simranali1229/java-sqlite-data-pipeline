
package com.example;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class DataSeeder {

    public static void insertSampleData() {

        try (Connection connection = DatabaseConnection.connect()) {

            connection.setAutoCommit(false);

            try {
                // Insert products
                String productSQL = """
                    INSERT OR IGNORE INTO products
                    (product_id, product_name, category, price)
                    VALUES (?, ?, ?, ?)
                    """;

                try (PreparedStatement ps = connection.prepareStatement(productSQL)) {

                    Object[][] products = {
                        {1, "Laptop", "Electronics", 55000.0},
                        {2, "Mobile", "Electronics", 20000.0},
                        {3, "Headphones", "Accessories", 1500.0},
                        {4, "Keyboard", "Accessories", 1200.0},
                        {5, "Monitor", "Electronics", 12000.0}
                    };

                    for (Object[] p : products) {
                        ps.setInt(1, (Integer) p[0]);
                        ps.setString(2, (String) p[1]);
                        ps.setString(3, (String) p[2]);
                        ps.setDouble(4, (Double) p[3]);
                        ps.addBatch();
                    }

                    ps.executeBatch();
                }

                // Insert customers
                String customerSQL = """
                    INSERT OR IGNORE INTO customers
                    (customer_id, customer_name, email, city)
                    VALUES (?, ?, ?, ?)
                    """;

                try (PreparedStatement ps = connection.prepareStatement(customerSQL)) {

                    Object[][] customers = {
                        {1, "Rahul", "rahul@example.com", "Bangalore"},
                        {2, "Priya", "priya@example.com", "Hyderabad"},
                        {3, "Amit", "amit@example.com", "Delhi"},
                        {4, "Sneha", "sneha@example.com", "Mumbai"},
                        {5, "Arjun", "arjun@example.com", "Chennai"}
                    };

                    for (Object[] c : customers) {
                        ps.setInt(1, (Integer) c[0]);
                        ps.setString(2, (String) c[1]);
                        ps.setString(3, (String) c[2]);
                        ps.setString(4, (String) c[3]);
                        ps.addBatch();
                    }

                    ps.executeBatch();
                }

                // Insert orders
                String orderSQL = """
                    INSERT OR IGNORE INTO orders
                    (order_id, customer_id, product_id, quantity, unit_price, order_date)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """;

                try (PreparedStatement ps = connection.prepareStatement(orderSQL)) {

                    Object[][] orders = {
                        {1, 1, 1, 1, 55000.0, "2026-11-10"},
                        {2, 2, 2, 2, 20000.0, "2026-11-10"},
                        {3, 3, 3, 1, 1500.0, "2026-11-11"},
                        {4, 1, 4, 2, 1200.0, "2026-11-11"},
                        {5, 4, 5, 1, 12000.0, "2026-11-12"},
                        {6, 5, 2, 1, 20000.0, "2026-11-12"},
                        {7, 2, 3, 3, 1500.0, "2026-11-13"},
                        {8, 3, 1, 1, 55000.0, "2026-11-13"}
                    };

                    for (Object[] o : orders) {
                        ps.setInt(1, (Integer) o[0]);
                        ps.setInt(2, (Integer) o[1]);
                        ps.setInt(3, (Integer) o[2]);
                        ps.setInt(4, (Integer) o[3]);
                        ps.setDouble(5, (Double) o[4]);
                        ps.setString(6, (String) o[5]);
                        ps.addBatch();
                    }

                    ps.executeBatch();
                }

                connection.commit();

                System.out.println("Sample products inserted!");
                System.out.println("Sample customers inserted!");
                System.out.println("Sample orders inserted!");

            } catch (SQLException e) {
                connection.rollback();
                throw e;
            }

        } catch (SQLException e) {
            System.out.println("Error inserting sample data!");
            e.printStackTrace();
        }
    }
}
