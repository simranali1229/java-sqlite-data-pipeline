
package com.example;

import java.sql.Connection;
import java.sql.Statement;

public class AddPerformanceIndex {

    public static void main(String[] args) {

        System.setProperty("db.url", "jdbc:sqlite:test_metrics.db");

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

            System.out.println("Performance index created successfully!");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
