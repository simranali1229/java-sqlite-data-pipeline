package com.example;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;

public class DatabaseVerification {

    public static void main(String[] args) {

        try (Connection connection = DatabaseConnection.connect();
             Statement statement = connection.createStatement()) {

            System.out.println("\n========== RECENT PIPELINE RUNS ==========");

            try (ResultSet rs = statement.executeQuery(
                    "SELECT * FROM pipeline_runs ORDER BY rowid DESC LIMIT 5")) {

                ResultSetMetaData metadata = rs.getMetaData();
                int columns = metadata.getColumnCount();

                while (rs.next()) {
                    System.out.println("\n--------------------------------");

                    for (int i = 1; i <= columns; i++) {
                        System.out.println(
                            metadata.getColumnLabel(i) + " : "
                            + rs.getString(i)
                        );
                    }
                }
            }

            System.out.println("\n========== VERIFICATION COMPLETE ==========");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
