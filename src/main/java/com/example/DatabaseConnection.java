package com.example;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DatabaseConnection {

    private static final String DEFAULT_URL =
            "jdbc:sqlite:product_metrics.db";

    public static Connection connect() throws SQLException {

        String url = System.getProperty("db.url", DEFAULT_URL);

        return connect(url);
    }

    public static Connection connect(String url) throws SQLException {

        // Validate database URL
        if (url == null || url.isBlank()) {
            throw new SQLException(
                    "Database URL cannot be empty.");
        }

        // Load SQLite JDBC driver
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            throw new SQLException(
                    "SQLite JDBC driver not found. Check Maven dependency.",
                    e
            );
        }

        // Establish database connection
        Connection connection = DriverManager.getConnection(url);

        try {
            // Enable foreign-key validation
            try (var statement = connection.createStatement()) {
                statement.execute("PRAGMA foreign_keys = ON");
            }

            return connection;

        } catch (SQLException e) {

            // Close connection if configuration fails
            try {
                connection.close();
            } catch (SQLException closeException) {
                e.addSuppressed(closeException);
            }

            throw e;
        }
    }
}
