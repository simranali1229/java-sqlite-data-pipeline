package com.example;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Savepoint;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class EventProcessor {

    private static final String EXPECTED_HEADER =
            "event_id,order_id,event_type,event_version,customer_id," +
            "product_id,quantity,unit_price,business_date,event_timestamp";

    public static void processCsv(String fileName) throws Exception {

        String runId = UUID.randomUUID().toString();
        Path filePath = Path.of(fileName);

        createPipelineRun(runId, filePath.getFileName().toString());

        int inputCount = 0;
        int acceptedCount = 0;
        int rejectedCount = 0;
        int duplicateCount = 0;

        try (Connection connection = DatabaseConnection.connect()) {

            enableForeignKeys(connection);
            connection.setAutoCommit(false);

            try {
                List<String> lines =
                        Files.readAllLines(filePath, StandardCharsets.UTF_8);

                if (lines.isEmpty() ||
                        !lines.get(0).trim().equals(EXPECTED_HEADER)) {
                    throw new IllegalArgumentException(
                            "CSV header is missing or incorrect.");
                }

                for (int i = 1; i < lines.size(); i++) {

                    String rawRecord = lines.get(i).trim();

                    if (rawRecord.isEmpty()) {
                        continue;
                    }

                    inputCount++;
                    int sourceLine = i + 1;
                    Savepoint savepoint = connection.setSavepoint();

                    try {
                        String[] values = rawRecord.split(",", -1);

                        if (values.length != 10) {
                            throw new IllegalArgumentException(
                                    "Expected 10 comma-separated columns.");
                        }

                        String eventId = values[0].trim();

                        if (eventId.isEmpty()) {
                            throw new IllegalArgumentException(
                                    "Event ID cannot be empty.");
                        }

                        if (isDuplicate(connection, eventId)) {
                            duplicateCount++;
                            connection.rollback(savepoint);
                            connection.releaseSavepoint(savepoint);
                            System.out.println(
                                    "DUPLICATE SKIPPED: " + eventId);
                            continue;
                        }

                        Event event = parseEvent(values);

                        validateReferences(connection, event);
                        applyEvent(connection, event);

                        insertEventHistory(
                                connection, event,
                                filePath.getFileName().toString(),
                                sourceLine);

                        markEventProcessed(connection, eventId, runId);

                        acceptedCount++;
                        connection.releaseSavepoint(savepoint);

                        System.out.println(
                                "EVENT PROCESSED: " + event.eventId);

                    } catch (Exception e) {

                        connection.rollback(savepoint);
                        connection.releaseSavepoint(savepoint);

                        insertRejectedRecord(
                                connection,
                                runId,
                                filePath.getFileName().toString(),
                                sourceLine,
                                rawRecord,
                                e.getMessage());

                        rejectedCount++;

                        System.out.println(
                                "EVENT REJECTED at line " +
                                sourceLine + ": " + e.getMessage());
                    }
                }

                updateRun(
                        connection, runId, "SUCCESS",
                        inputCount, acceptedCount,
                        rejectedCount, duplicateCount, null);

                connection.commit();

            } catch (Exception e) {
                connection.rollback();
                throw e;
            }

        } catch (Exception e) {
            updateFailedRun(runId, e.getMessage());
            throw e;
        }

        System.out.println("\nEVENT PROCESSING SUMMARY");
        System.out.println("------------------------");
        System.out.println("Run ID: " + runId);
        System.out.println("Input records: " + inputCount);
        System.out.println("Accepted: " + acceptedCount);
        System.out.println("Rejected: " + rejectedCount);
        System.out.println("Duplicates: " + duplicateCount);
    }

    private static void enableForeignKeys(Connection connection)
            throws SQLException {
        try (PreparedStatement ps =
                     connection.prepareStatement("PRAGMA foreign_keys = ON")) {
            ps.execute();
        }
    }

    private static void createPipelineRun(
            String runId, String sourceFile) throws SQLException {

        String sql = """
                INSERT INTO pipeline_runs
                (run_id, source_file, started_at, status,
                 input_count, accepted_count, rejected_count, duplicate_count)
                VALUES (?, ?, ?, 'RUNNING', 0, 0, 0, 0)
                """;

        try (Connection connection = DatabaseConnection.connect();
             PreparedStatement ps = connection.prepareStatement(sql)) {

            ps.setString(1, runId);
            ps.setString(2, sourceFile);
            ps.setString(3, Instant.now().toString());
            ps.executeUpdate();
        }
    }

    private static Event parseEvent(String[] v) {

        String eventId = v[0].trim();
        int orderId = Integer.parseInt(v[1].trim());
        String eventType = v[2].trim().toUpperCase();
        int version = Integer.parseInt(v[3].trim());
        int customerId = Integer.parseInt(v[4].trim());
        int productId = Integer.parseInt(v[5].trim());
        int quantity = Integer.parseInt(v[6].trim());
        double unitPrice = Double.parseDouble(v[7].trim());

        LocalDate businessDate = LocalDate.parse(v[8].trim());
        LocalDateTime eventTimestamp =
                LocalDateTime.parse(v[9].trim());

        if (!eventType.equals("CREATE")
                && !eventType.equals("UPDATE")
                && !eventType.equals("REVERSE")) {
            throw new IllegalArgumentException(
                    "Event type must be CREATE, UPDATE or REVERSE.");
        }

        if (version < 1) {
            throw new IllegalArgumentException(
                    "Event version must be at least 1.");
        }

        if (quantity <= 0) {
            throw new IllegalArgumentException(
                    "Quantity must be greater than zero.");
        }

        if (unitPrice < 0) {
            throw new IllegalArgumentException(
                    "Unit price cannot be negative.");
        }

        return new Event(
                eventId, orderId, eventType, version,
                customerId, productId, quantity, unitPrice,
                businessDate.toString(), eventTimestamp.toString());
    }

    private static boolean isDuplicate(
            Connection connection, String eventId)
            throws SQLException {

        String sql =
                "SELECT 1 FROM processed_events WHERE event_id = ?";

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, eventId);

            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

private static void validateReferences(
        Connection connection, Event event)
        throws SQLException {

    checkReference(
            connection, "customers", "customer_id",
            event.customerId, "Customer");

    checkReference(
            connection, "products", "product_id",
            event.productId, "Product");
}

private static void checkReference(
        Connection connection,
        String table,
        String column,
        int id,
        String label) throws SQLException {

    String sql = "SELECT 1 FROM " + table +
                 " WHERE " + column + " = ?";

    try (PreparedStatement ps = connection.prepareStatement(sql)) {
        ps.setInt(1, id);

        try (ResultSet rs = ps.executeQuery()) {
            if (!rs.next()) {
                throw new IllegalArgumentException(
                        label + " ID does not exist: " + id);
            }
        }
    }
}
    private static void applyEvent(
            Connection connection, Event event)
            throws SQLException {

        String selectSql = """
                SELECT latest_version, is_active
                FROM transaction_state
                WHERE order_id = ?
                """;

        int latestVersion = 0;
        boolean exists = false;
        boolean active = false;

        try (PreparedStatement ps =
                     connection.prepareStatement(selectSql)) {

            ps.setInt(1, event.orderId);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    exists = true;
                    latestVersion = rs.getInt("latest_version");
                    active = rs.getInt("is_active") == 1;
                }
            }
        }

        if (event.eventType.equals("CREATE")) {

            if (exists) {
                throw new IllegalArgumentException(
                        "Cannot CREATE: order already exists.");
            }

            if (event.version != 1) {
                throw new IllegalArgumentException(
                        "CREATE must start at version 1.");
            }

            String sql = """
                    INSERT INTO transaction_state
                    (order_id, customer_id, product_id, quantity,
                     unit_price, business_date, latest_version,
                     is_active, last_event_id, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, 1, ?, ?)
                    """;

            try (PreparedStatement ps =
                         connection.prepareStatement(sql)) {
                ps.setInt(1, event.orderId);
                ps.setInt(2, event.customerId);
                ps.setInt(3, event.productId);
                ps.setInt(4, event.quantity);
                ps.setDouble(5, event.unitPrice);
                ps.setString(6, event.businessDate);
                ps.setInt(7, event.version);
                ps.setString(8, event.eventId);
                ps.setString(9, Instant.now().toString());
                ps.executeUpdate();
            }

            return;
        }

        if (!exists) {
            throw new IllegalArgumentException(
                    "Order does not exist: " + event.orderId);
        }

        if (!active) {
            throw new IllegalArgumentException(
                    "Order is already reversed or inactive.");
        }

        if (event.version != latestVersion + 1) {
            throw new IllegalArgumentException(
                    "Incorrect event version. Expected " +
                    (latestVersion + 1) + " but received " +
                    event.version);
        }

        String sql;

        if (event.eventType.equals("UPDATE")) {
            sql = """
                    UPDATE transaction_state
                    SET customer_id = ?, product_id = ?, quantity = ?,
                        unit_price = ?, business_date = ?,
                        latest_version = ?, last_event_id = ?,
                        updated_at = ?
                    WHERE order_id = ? AND latest_version = ?
                      AND is_active = 1
                    """;
        } else {
            sql = """
                    UPDATE transaction_state
                    SET is_active = 0, latest_version = ?,
                        last_event_id = ?, updated_at = ?
                    WHERE order_id = ? AND latest_version = ?
                      AND is_active = 1
                    """;
        }

        try (PreparedStatement ps = connection.prepareStatement(sql)) {

            if (event.eventType.equals("UPDATE")) {
                ps.setInt(1, event.customerId);
                ps.setInt(2, event.productId);
                ps.setInt(3, event.quantity);
                ps.setDouble(4, event.unitPrice);
                ps.setString(5, event.businessDate);
                ps.setInt(6, event.version);
                ps.setString(7, event.eventId);
                ps.setString(8, Instant.now().toString());
                ps.setInt(9, event.orderId);
                ps.setInt(10, latestVersion);
            } else {
                ps.setInt(1, event.version);
                ps.setString(2, event.eventId);
                ps.setString(3, Instant.now().toString());
                ps.setInt(4, event.orderId);
                ps.setInt(5, latestVersion);
            }

            if (ps.executeUpdate() != 1) {
                throw new SQLException(
                        "Order state update failed.");
            }
        }
    }

    private static void insertEventHistory(
            Connection connection,
            Event e,
            String sourceFile,
            int sourceLine) throws SQLException {

        String sql = """
                INSERT INTO event_history
                (event_id, order_id, event_type, event_version,
                 customer_id, product_id, quantity, unit_price,
                 business_date, event_timestamp, source_file,
                 source_line, processed_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, e.eventId);
            ps.setInt(2, e.orderId);
            ps.setString(3, e.eventType);
            ps.setInt(4, e.version);
            ps.setInt(5, e.customerId);
            ps.setInt(6, e.productId);
            ps.setInt(7, e.quantity);
            ps.setDouble(8, e.unitPrice);
            ps.setString(9, e.businessDate);
            ps.setString(10, e.eventTimestamp);
            ps.setString(11, sourceFile);
            ps.setInt(12, sourceLine);
            ps.setString(13, Instant.now().toString());
            ps.executeUpdate();
        }
    }

    private static void markEventProcessed(
            Connection connection,
            String eventId,
            String runId) throws SQLException {

        String sql = """
                INSERT INTO processed_events
                (event_id, run_id, processed_at)
                VALUES (?, ?, ?)
                """;

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, eventId);
            ps.setString(2, runId);
            ps.setString(3, Instant.now().toString());
            ps.executeUpdate();
        }
    }

    private static void insertRejectedRecord(
            Connection connection,
            String runId,
            String sourceFile,
            int sourceLine,
            String rawRecord,
            String error) throws SQLException {

        String sql = """
                INSERT INTO rejected_records
                (run_id, source_file, source_line,
                 raw_record, error_message, rejected_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """;

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, runId);
            ps.setString(2, sourceFile);
            ps.setInt(3, sourceLine);
            ps.setString(4, rawRecord);
            ps.setString(5, error == null ? "Invalid record" : error);
            ps.setString(6, Instant.now().toString());
            ps.executeUpdate();
        }
    }

    private static void updateRun(
            Connection connection,
            String runId,
            String status,
            int input,
            int accepted,
            int rejected,
            int duplicates,
            String error) throws SQLException {

        String sql = """
                UPDATE pipeline_runs
                SET finished_at = ?, status = ?, input_count = ?,
                    accepted_count = ?, rejected_count = ?,
                    duplicate_count = ?, error_message = ?
                WHERE run_id = ?
                """;

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, Instant.now().toString());
            ps.setString(2, status);
            ps.setInt(3, input);
            ps.setInt(4, accepted);
            ps.setInt(5, rejected);
            ps.setInt(6, duplicates);
            ps.setString(7, error);
            ps.setString(8, runId);
            ps.executeUpdate();
        }
    }

    private static void updateFailedRun(
            String runId, String error) {

        String sql = """
                UPDATE pipeline_runs
                SET finished_at = ?, status = 'FAILED',
                    error_message = ?
                WHERE run_id = ?
                """;

        try (Connection connection = DatabaseConnection.connect();
             PreparedStatement ps = connection.prepareStatement(sql)) {

            ps.setString(1, Instant.now().toString());
            ps.setString(2, error);
            ps.setString(3, runId);
            ps.executeUpdate();

        } catch (SQLException e) {
            System.err.println(
                    "Could not update failed pipeline run: " +
                    e.getMessage());
        }
    }

    private static class Event {
        String eventId;
        int orderId;
        String eventType;
        int version;
        int customerId;
        int productId;
        int quantity;
        double unitPrice;
        String businessDate;
        String eventTimestamp;

        Event(String eventId, int orderId, String eventType,
              int version, int customerId, int productId,
              int quantity, double unitPrice,
              String businessDate, String eventTimestamp) {

            this.eventId = eventId;
            this.orderId = orderId;
            this.eventType = eventType;
            this.version = version;
            this.customerId = customerId;
            this.productId = productId;
            this.quantity = quantity;
            this.unitPrice = unitPrice;
            this.businessDate = businessDate;
            this.eventTimestamp = eventTimestamp;
        }
    }
}
