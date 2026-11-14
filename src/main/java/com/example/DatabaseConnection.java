
package com.example;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DatabaseConnection {

    private static final String URL = "jdbc:sqlite:product_metrics.db";

    public static Connection connect() throws SQLException {

        try {
            // Explicitly load SQLite JDBC driver
            Class.forName("org.sqlite.JDBC");

        } catch (ClassNotFoundException e) {
            throw new SQLException("SQLite JDBC driver not found. Check Maven dependency.", e);
        }

        return DriverManager.getConnection(URL);
    }
}
