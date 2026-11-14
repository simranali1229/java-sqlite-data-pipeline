
package com.example;

import java.sql.Connection;
import java.sql.Statement;
import java.sql.SQLException;

public class DatabaseSetup {

    public static void createTables() {

        String productsTable = """
            CREATE TABLE IF NOT EXISTS products (
                product_id INTEGER PRIMARY KEY AUTOINCREMENT,
                product_name TEXT NOT NULL,
                category TEXT,
                price REAL NOT NULL CHECK(price >= 0)
            )
            """;

        String customersTable = """
            CREATE TABLE IF NOT EXISTS customers (
                customer_id INTEGER PRIMARY KEY AUTOINCREMENT,
                customer_name TEXT NOT NULL,
                email TEXT UNIQUE,
                city TEXT
            )
            """;

        String ordersTable = """
            CREATE TABLE IF NOT EXISTS orders (
                order_id INTEGER PRIMARY KEY AUTOINCREMENT,
                customer_id INTEGER NOT NULL,
                product_id INTEGER NOT NULL,
                quantity INTEGER NOT NULL CHECK(quantity > 0),
                unit_price REAL NOT NULL,
                order_date TEXT NOT NULL,
                FOREIGN KEY(customer_id) REFERENCES customers(customer_id),
                FOREIGN KEY(product_id) REFERENCES products(product_id)
            )
            """;

        try (Connection connection = DatabaseConnection.connect();
             Statement statement = connection.createStatement()) {

            statement.execute("PRAGMA foreign_keys = ON");

            statement.execute(productsTable);
            statement.execute(customersTable);
            statement.execute(ordersTable);

            System.out.println("Products table created!");
            System.out.println("Customers table created!");
            System.out.println("Orders table created!");

        } catch (SQLException e) {
            System.out.println("Error creating database tables!");
            e.printStackTrace();
        }
    }
}
