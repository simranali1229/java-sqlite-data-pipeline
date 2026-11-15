package com.example;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class ReconciliationReport {

    public static void main(String[] args) {

        int exitCode = runReconciliation();

        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }

    public static int runReconciliation() {

        try (Connection connection = DatabaseConnection.connect();
             Statement statement = connection.createStatement()) {

            boolean overallPassed = true;

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

            // 2. Display stored daily product metrics
            String metricsSql =
                    "SELECT m.business_date, p.product_name, " +
                    "m.order_count, m.total_quantity, m.total_revenue " +
                    "FROM daily_product_metrics m " +
                    "JOIN products p ON m.product_id = p.product_id " +
                    "ORDER BY m.business_date, p.product_name";

            System.out.println("\n--- STORED DAILY PRODUCT METRICS ---");

            try (ResultSet rs = statement.executeQuery(metricsSql)) {

                System.out.printf(
                        "%-15s %-20s %12s %12s %15s%n",
                        "DATE", "PRODUCT", "ORDERS", "QUANTITY", "REVENUE"
                );

                while (rs.next()) {

                    System.out.printf(
                            "%-15s %-20s %12d %12d %15.2f%n",
                            rs.getString("business_date"),
                            rs.getString("product_name"),
                            rs.getLong("order_count"),
                            rs.getLong("total_quantity"),
                            rs.getDouble("total_revenue")
                    );
                }
            }

            // 3. Compare event IDs in both tables
            System.out.println("\n--- EVENT RECONCILIATION ---");

            String missingEventsSql =
                    "SELECT COUNT(*) FROM (" +
                    "SELECT event_id FROM event_history " +
                    "EXCEPT " +
                    "SELECT event_id FROM processed_events" +
                    ")";

            String extraEventsSql =
                    "SELECT COUNT(*) FROM (" +
                    "SELECT event_id FROM processed_events " +
                    "EXCEPT " +
                    "SELECT event_id FROM event_history" +
                    ")";

            long missingEvents = getCount(statement, missingEventsSql);
            long extraEvents = getCount(statement, extraEventsSql);

            System.out.println("Event IDs missing from processed_events : " +
                    missingEvents);

            System.out.println("Event IDs missing from event_history    : " +
                    extraEvents);

            boolean eventPassed =
                    missingEvents == 0 && extraEvents == 0;

            printStatus("Event ID reconciliation", eventPassed);

            if (!eventPassed) {
                overallPassed = false;
            }

            // 4. Compare stored daily metrics with actual active orders
            System.out.println("\n--- DAILY METRICS RECONCILIATION ---");

            String metricsCheckSql =
                    "WITH expected AS (" +
                    " SELECT business_date, product_id, " +
                    " COUNT(*) AS order_count, " +
                    " SUM(quantity) AS total_quantity, " +
                    " SUM(quantity * unit_price) AS total_revenue " +
                    " FROM transaction_state " +
                    " WHERE is_active = 1 " +
                    " GROUP BY business_date, product_id" +
                    "), metric_keys AS (" +
                    " SELECT business_date, product_id FROM expected " +
                    " UNION " +
                    " SELECT business_date, product_id FROM daily_product_metrics" +
                    "), comparison AS (" +
                    " SELECT k.business_date, k.product_id, " +
                    " COALESCE(e.order_count, 0) AS expected_orders, " +
                    " COALESCE(m.order_count, 0) AS stored_orders, " +
                    " COALESCE(e.total_quantity, 0) AS expected_quantity, " +
                    " COALESCE(m.total_quantity, 0) AS stored_quantity, " +
                    " COALESCE(e.total_revenue, 0) AS expected_revenue, " +
                    " COALESCE(m.total_revenue, 0) AS stored_revenue " +
                    " FROM metric_keys k " +
                    " LEFT JOIN expected e " +
                    " ON k.business_date = e.business_date " +
                    " AND k.product_id = e.product_id " +
                    " LEFT JOIN daily_product_metrics m " +
                    " ON k.business_date = m.business_date " +
                    " AND k.product_id = m.product_id" +
                    ") " +
                    "SELECT COUNT(*) FROM comparison " +
                    "WHERE expected_orders <> stored_orders " +
                    "OR expected_quantity <> stored_quantity " +
                    "OR ABS(expected_revenue - stored_revenue) > 0.01";

            long metricMismatches = getCount(statement, metricsCheckSql);

            System.out.println("Daily metric mismatches : " + metricMismatches);

            boolean metricsPassed = metricMismatches == 0;

            printStatus("Daily metrics reconciliation", metricsPassed);

            if (!metricsPassed) {
                overallPassed = false;
            }

            // 5. Compare event history and processed event counts
            long historyCount = getCount(
                    statement,
                    "SELECT COUNT(*) FROM event_history"
            );

            long processedCount = getCount(
                    statement,
                    "SELECT COUNT(*) FROM processed_events"
            );

            System.out.println("\n--- EVENT RECORD COUNTS ---");

            System.out.println("Event history records   : " + historyCount);
            System.out.println("Processed event records : " + processedCount);

            boolean eventCountsPassed = historyCount == processedCount;

            printStatus("Event record count check", eventCountsPassed);

            if (!eventCountsPassed) {
                overallPassed = false;
            }

            // 6. Verify pipeline run counts
            String runCheckSql =
                    "SELECT COUNT(*) FROM pipeline_runs " +
                    "WHERE COALESCE(input_count, 0) <> " +
                    "(COALESCE(accepted_count, 0) + " +
                    "COALESCE(rejected_count, 0) + " +
                    "COALESCE(duplicate_count, 0))";

            long inconsistentRuns = getCount(statement, runCheckSql);

            System.out.println("\n--- PIPELINE RUN CHECK ---");

            System.out.println("Inconsistent pipeline runs : " + inconsistentRuns);

            boolean pipelinePassed = inconsistentRuns == 0;

            printStatus("Pipeline reconciliation", pipelinePassed);

            if (!pipelinePassed) {
                overallPassed = false;
            }

            // 7. Overall reconciliation result
            System.out.println("\n========== FINAL RESULT ==========");

            if (overallPassed) {

                System.out.println("OVERALL RECONCILIATION: PASSED");
                System.out.println("All reconciliation checks passed.");

                System.out.println("==================================");

                return 0;

            } else {

                System.out.println("OVERALL RECONCILIATION: FAILED");
                System.out.println("Please investigate the failed checks.");

                System.out.println("==================================");

                return 1;
            }

        } catch (Exception e) {

            System.err.println("Reconciliation failed: " + e.getMessage());
            e.printStackTrace();

            return 1;
        }
    }

    // Execute a query that returns a single count
    private static long getCount(Statement statement, String sql)
            throws SQLException {

        try (ResultSet rs = statement.executeQuery(sql)) {

            if (rs.next()) {
                return rs.getLong(1);
            }
        }

        return 0;
    }

    // Print reconciliation status
    private static void printStatus(String checkName, boolean passed) {

        System.out.println(
                checkName + " : " + (passed ? "PASSED" : "FAILED")
        );
    }
}
