package com.example;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

public class ReconciliationReport {

    public static void main(String[] args) {

        try (Connection connection = DatabaseConnection.connect();
             Statement statement = connection.createStatement()) {

            System.out.println("\n========== DATA RECONCILIATION REPORT ==========");

            // 1. Active orders, reversed orders and revenue
            String summarySql =
                "SELECT " +
                "COUNT(CASE WHEN is_active = 1 THEN 1 END) AS active_orders, " +
                "COUNT(CASE WHEN is_active = 0 THEN 1 END) AS reversed_orders, " +
                "COALESCE(SUM(CASE WHEN is_active = 1 " +
                "THEN quantity * unit_price ELSE 0 END), 0) AS total_revenue, " +
                "COALESCE(SUM(CASE WHEN is_active = 1 " +
                "THEN quantity ELSE 0 END), 0) AS total_quantity " +
                "FROM transaction_state";

            try (ResultSet rs = statement.executeQuery(summarySql)) {
                if (rs.next()) {
                    System.out.println("\n--- ORDER SUMMARY ---");
                    System.out.println("Active orders   : " +
                            rs.getLong("active_orders"));
                    System.out.println("Reversed orders : " +
                            rs.getLong("reversed_orders"));
                    System.out.println("Total quantity  : " +
                            rs.getLong("total_quantity"));
                    System.out.printf("Total revenue   : %.2f%n",
                            rs.getDouble("total_revenue"));
                }
            }

            // 2. Daily product metrics
            String metricsSql =
                "SELECT s.business_date, p.product_name, " +
                "COUNT(*) AS order_count, " +
                "SUM(s.quantity) AS total_quantity, " +
                "SUM(s.quantity * s.unit_price) AS revenue " +
                "FROM transaction_state s " +
                "JOIN products p ON s.product_id = p.product_id " +
                "WHERE s.is_active = 1 " +
                "GROUP BY s.business_date, p.product_id, p.product_name " +
                "ORDER BY s.business_date, p.product_name";

            System.out.println("\n--- DAILY PRODUCT METRICS ---");

            try (ResultSet rs = statement.executeQuery(metricsSql)) {
                System.out.printf("%-15s %-20s %12s %12s %15s%n",
                        "DATE", "PRODUCT", "ORDERS", "QUANTITY", "REVENUE");

                while (rs.next()) {
                    System.out.printf("%-15s %-20s %12d %12d %15.2f%n",
                            rs.getString("business_date"),
                            rs.getString("product_name"),
                            rs.getLong("order_count"),
                            rs.getLong("total_quantity"),
                            rs.getDouble("revenue"));
                }
            }

            // 3. Compare event history and processed event counts
            long historyCount = getCount(
                    statement, "SELECT COUNT(*) FROM event_history");

            long processedCount = getCount(
                    statement, "SELECT COUNT(*) FROM processed_events");

            System.out.println("\n--- EVENT RECONCILIATION ---");
            System.out.println("Event history records   : " + historyCount);
            System.out.println("Processed event records : " + processedCount);

            if (historyCount == processedCount) {
                System.out.println("Event reconciliation   : PASSED");
            } else {
                System.out.println("Event reconciliation   : FAILED");
            }

            // 4. Verify pipeline run counts
            String runCheckSql =
                "SELECT COUNT(*) FROM pipeline_runs " +
                "WHERE COALESCE(input_count, 0) <> " +
                "(COALESCE(accepted_count, 0) + " +
                "COALESCE(rejected_count, 0) + " +
                "COALESCE(duplicate_count, 0))";

            long inconsistentRuns = getCount(statement, runCheckSql);

            System.out.println("\n--- PIPELINE RUN CHECK ---");
            System.out.println("Inconsistent pipeline runs : " + inconsistentRuns);

            if (inconsistentRuns == 0) {
                System.out.println("Pipeline reconciliation   : PASSED");
            } else {
                System.out.println("Pipeline reconciliation   : FAILED");
            }

            System.out.println("\n========== REPORT COMPLETED ==========");

        } catch (Exception e) {
            System.out.println("Reconciliation failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static long getCount(Statement statement, String sql)
            throws Exception {

        try (ResultSet rs = statement.executeQuery(sql)) {
            if (rs.next()) {
                return rs.getLong(1);
            }
        }

        return 0;
    }
}
