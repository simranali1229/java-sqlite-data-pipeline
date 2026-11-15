package com.example;

import java.sql.Connection;
import java.sql.Statement;

public class AddIndexToDatabase {

    public static void main(String[] args) {

        if (args.length != 1) {
            System.out.println("Provide database filename.");
            return;
        }

        System.setProperty(
            "db.url",
            "jdbc:sqlite:" + args[0]
        );

        String sql = """
            CREATE INDEX IF NOT EXISTS
            idx_state_date_product_active
            ON transaction_state(
                business_date,
                product_id,
                is_active
            )
            """;

        try (Connection connection = DatabaseConnection.connect();
             Statement statement = connection.createStatement()) {

            statement.execute(sql);
            System.out.println("Performance index created!");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
