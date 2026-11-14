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

        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            throw new SQLException(
                    "SQLite JDBC driver not found. Check Maven dependency.", e);
        }

        Connection connection = DriverManager.getConnection(url);

        // Enable foreign-key validation for this connection
        try (var statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON");
        }

        return connection;
    }
}
