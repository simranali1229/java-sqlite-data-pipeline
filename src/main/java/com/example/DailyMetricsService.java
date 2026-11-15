package com.example;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Objects;

public class DailyMetricsService implements AutoCloseable {

    private final PreparedStatement findStateStatement;
    private final PreparedStatement deleteMetricStatement;
    private final PreparedStatement insertMetricStatement;

    public DailyMetricsService(Connection connection)
            throws SQLException {

        String findStateSql = """
                SELECT business_date, product_id
                FROM transaction_state
                WHERE order_id = ?
                """;

        String deleteSql = """
                DELETE FROM daily_product_metrics
                WHERE business_date = ?
                  AND product_id = ?
                """;

        String insertSql = """
                INSERT INTO daily_product_metrics (
                    business_date,
                    product_id,
                    order_count,
                    total_quantity,
                    total_revenue,
                    updated_at
                )
                SELECT
                    business_date,
                    product_id,
                    COUNT(*),
                    SUM(quantity),
                    SUM(quantity * unit_price),
                    ?
                FROM transaction_state
                WHERE business_date = ?
                  AND product_id = ?
                  AND is_active = 1
                GROUP BY business_date, product_id
                """;

        findStateStatement =
                connection.prepareStatement(findStateSql);

        deleteMetricStatement =
                connection.prepareStatement(deleteSql);

        insertMetricStatement =
                connection.prepareStatement(insertSql);
    }

    public void recomputeForOrderChange(
            String oldBusinessDate,
            Integer oldProductId,
            int orderId) throws SQLException {

        String newBusinessDate = null;
        Integer newProductId = null;

        findStateStatement.setInt(1, orderId);

        try (ResultSet rs = findStateStatement.executeQuery()) {
            if (rs.next()) {
                newBusinessDate = rs.getString("business_date");
                newProductId = rs.getInt("product_id");
            }
        }

        boolean sameMetricGroup =
                oldBusinessDate != null
                && oldProductId != null
                && newBusinessDate != null
                && newProductId != null
                && Objects.equals(oldBusinessDate, newBusinessDate)
                && oldProductId.equals(newProductId);

        if (sameMetricGroup) {
            recomputeKey(newBusinessDate, newProductId);
            return;
        }

        if (oldBusinessDate != null && oldProductId != null) {
            recomputeKey(oldBusinessDate, oldProductId);
        }

        if (newBusinessDate != null && newProductId != null) {
            recomputeKey(newBusinessDate, newProductId);
        }
    }

    private void recomputeKey(
            String businessDate,
            int productId) throws SQLException {

        deleteMetricStatement.setString(1, businessDate);
        deleteMetricStatement.setInt(2, productId);
        deleteMetricStatement.executeUpdate();

        insertMetricStatement.setString(
                1, Instant.now().toString());
        insertMetricStatement.setString(2, businessDate);
        insertMetricStatement.setInt(3, productId);
        insertMetricStatement.executeUpdate();
    }

    @Override
    public void close() throws SQLException {

        SQLException error = null;

        try {
            findStateStatement.close();
        } catch (SQLException e) {
            error = e;
        }

        try {
            deleteMetricStatement.close();
        } catch (SQLException e) {
            if (error == null) {
                error = e;
            } else {
                error.addSuppressed(e);
            }
        }

        try {
            insertMetricStatement.close();
        } catch (SQLException e) {
            if (error == null) {
                error = e;
            } else {
                error.addSuppressed(e);
            }
        }

        if (error != null) {
            throw error;
        }
    }
}
