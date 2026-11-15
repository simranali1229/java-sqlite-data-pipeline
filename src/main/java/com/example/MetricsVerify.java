
package com.example;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;

public class MetricsVerify {

    public static void main(String[] args) {

        System.setProperty(
            "db.url",
            "jdbc:sqlite:test_metrics.db"
        );

        String sql =
            "SELECT * FROM daily_product_metrics " +
            "WHERE business_date IN (?, ?) " +
            "AND product_id IN (?, ?) " +
            "ORDER BY business_date, product_id";

        try {
            Class.forName("org.sqlite.JDBC");

            try (Connection connection = DriverManager.getConnection(
                    "jdbc:sqlite:test_metrics.db");
                 PreparedStatement statement =
                    connection.prepareStatement(sql)) {

                statement.setString(1, "2099-11-10");
                statement.setString(2, "2099-11-12");
                statement.setInt(3, 1);
                statement.setInt(4, 2);

                try (ResultSet result = statement.executeQuery()) {

                    ResultSetMetaData metadata =
                        result.getMetaData();

                    int columns = metadata.getColumnCount();
                    int rowCount = 0;

                    System.out.println("DAILY METRICS VERIFICATION");
                    System.out.println("--------------------------");

                    while (result.next()) {
                        rowCount++;

                        for (int i = 1; i <= columns; i++) {
                            System.out.print(
                                metadata.getColumnName(i) +
                                " = " +
                                result.getString(i) +
                                " | "
                            );
                        }

                        System.out.println();
                    }

                    System.out.println("--------------------------");
                    System.out.println("Rows found: " + rowCount);
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
