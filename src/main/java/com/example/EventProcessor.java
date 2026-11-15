package com.example;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Savepoint;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class EventProcessor {

    private static final String EXPECTED_HEADER =
            "event_id,order_id,event_type,event_version,customer_id," +
            "product_id,quantity,unit_price,business_date,event_timestamp";

    public static void processCsv(String fileName) throws Exception {

        String runId = UUID.randomUUID().toString();
        Path filePath = Path.of(fileName);
        String sourceFile = filePath.getFileName().toString();

        createPipelineRun(runId, sourceFile);

        int inputCount = 0;
        int acceptedCount = 0;
        int rejectedCount = 0;
        int duplicateCount = 0;

        try (Connection connection = DatabaseConnection.connect()) {

            enableForeignKeys(connection);
            connection.setAutoCommit(false);

            try (EventStatements statements =
                         new EventStatements(connection);
                 DailyMetricsService metricsService =
                         new DailyMetricsService(connection)) {

                try {

                    List<String> lines =
                            Files.readAllLines(
                                    filePath, StandardCharsets.UTF_8);

                    if (lines.isEmpty()) {
                        throw new IllegalArgumentException(
                                "CSV header is missing.");
                    }

                    String headerLine = lines.get(0);

                    if (headerLine.startsWith("\uFEFF")) {
                        headerLine = headerLine.substring(1);
                    }

                    List<String> headerColumns =
                            parseCsvLine(headerLine);

                    if (!String.join(",", headerColumns)
                            .equals(EXPECTED_HEADER)) {
                        throw new IllegalArgumentException(
                                "CSV header is missing, incorrect, " +
                                "or has unexpected columns.");
                    }

                    for (int i = 1; i < lines.size(); i++) {

                        String rawRecord = lines.get(i);

                        if (rawRecord.isBlank()) {
                            continue;
                        }

                        inputCount++;
                        int sourceLine = i + 1;

                        Savepoint savepoint =
                                connection.setSavepoint();

                        try {

                            List<String> parsedValues =
                                    parseCsvLine(rawRecord);

                            if (parsedValues.size() != 10) {
                                throw new IllegalArgumentException(
                                        "Expected 10 CSV columns but received " +
                                        parsedValues.size() + ".");
                            }

                            String[] values =
                                    parsedValues.toArray(new String[0]);

                            validateRequiredFields(values);

                            String eventId = values[0].trim();

                            if (statements.isDuplicate(eventId)) {

                                duplicateCount++;

                                connection.rollback(savepoint);
                                connection.releaseSavepoint(savepoint);

                                continue;
                            }

                            Event event = parseEvent(values);

                            statements.validateReferences(event);

                            // STEP 1: Capture the old order state.
                            OrderState oldState =
                                    statements.findOldState(event.orderId);

                            // STEP 2: Apply CREATE, UPDATE or REVERSE.
                            statements.applyEvent(event);

                            // STEP 3: Recalculate daily metrics.
                            metricsService.recomputeForOrderChange(
                                    oldState.businessDate,
                                    oldState.productId,
                                    event.orderId
                            );

                            // STEP 4: Insert event history.
                            statements.insertEventHistory(
                                    event,
                                    sourceFile,
                                    sourceLine
                            );

                            // STEP 5: Mark event as processed.
                            statements.markEventProcessed(
                                    event.eventId,
                                    runId
                            );

                            acceptedCount++;

                            connection.releaseSavepoint(savepoint);

                        } catch (Exception e) {

                            connection.rollback(savepoint);
                            connection.releaseSavepoint(savepoint);

                            statements.insertRejectedRecord(
                                    runId,
                                    sourceFile,
                                    sourceLine,
                                    rawRecord,
                                    e.getMessage()
                            );

                            rejectedCount++;
                        }
                    }

                    statements.updateRun(
                            runId,
                            "SUCCESS",
                            inputCount,
                            acceptedCount,
                            rejectedCount,
                            duplicateCount,
                            null
                    );

                    connection.commit();

                } catch (Exception e) {

                    connection.rollback();
                    throw e;
                }
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

    // =====================================================
    // REUSABLE PREPARED STATEMENTS
    // =====================================================

    private static class EventStatements implements AutoCloseable {

        private final PreparedStatement duplicateStatement;
        private final PreparedStatement oldStateStatement;

        private final PreparedStatement customerStatement;
        private final PreparedStatement productStatement;

        private final PreparedStatement transactionSelectStatement;
        private final PreparedStatement transactionInsertStatement;
        private final PreparedStatement transactionUpdateStatement;
        private final PreparedStatement transactionReverseStatement;

        private final PreparedStatement historyStatement;
        private final PreparedStatement processedStatement;
        private final PreparedStatement rejectedStatement;
        private final PreparedStatement updateRunStatement;

        EventStatements(Connection connection) throws SQLException {

            duplicateStatement = connection.prepareStatement(
                    "SELECT 1 FROM processed_events WHERE event_id = ?"
            );

            oldStateStatement = connection.prepareStatement("""
                    SELECT business_date, product_id
                    FROM transaction_state
                    WHERE order_id = ?
                    """);

            customerStatement = connection.prepareStatement(
                    "SELECT 1 FROM customers WHERE customer_id = ?"
            );

            productStatement = connection.prepareStatement(
                    "SELECT 1 FROM products WHERE product_id = ?"
            );

            transactionSelectStatement =
                    connection.prepareStatement("""
                            SELECT latest_version, is_active
                            FROM transaction_state
                            WHERE order_id = ?
                            """);

            transactionInsertStatement =
                    connection.prepareStatement("""
                            INSERT INTO transaction_state
                            (order_id, customer_id, product_id, quantity,
                             unit_price, business_date, latest_version,
                             is_active, last_event_id, updated_at)
                            VALUES (?, ?, ?, ?, ?, ?, ?, 1, ?, ?)
                            """);

            transactionUpdateStatement =
                    connection.prepareStatement("""
                            UPDATE transaction_state
                            SET customer_id = ?, product_id = ?, quantity = ?,
                                unit_price = ?, business_date = ?,
                                latest_version = ?, last_event_id = ?,
                                updated_at = ?
                            WHERE order_id = ? AND latest_version = ?
                              AND is_active = 1
                            """);

            transactionReverseStatement =
                    connection.prepareStatement("""
                            UPDATE transaction_state
                            SET is_active = 0, latest_version = ?,
                                last_event_id = ?, updated_at = ?
                            WHERE order_id = ? AND latest_version = ?
                              AND is_active = 1
                            """);

            historyStatement = connection.prepareStatement("""
                    INSERT INTO event_history
                    (event_id, order_id, event_type, event_version,
                     customer_id, product_id, quantity, unit_price,
                     business_date, event_timestamp, source_file,
                     source_line, processed_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """);

            processedStatement = connection.prepareStatement("""
                    INSERT INTO processed_events
                    (event_id, run_id, processed_at)
                    VALUES (?, ?, ?)
                    """);

            rejectedStatement = connection.prepareStatement("""
                    INSERT INTO rejected_records
                    (run_id, source_file, source_line,
                     raw_record, error_message, rejected_at)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """);

            updateRunStatement = connection.prepareStatement("""
                    UPDATE pipeline_runs
                    SET finished_at = ?, status = ?, input_count = ?,
                        accepted_count = ?, rejected_count = ?,
                        duplicate_count = ?, error_message = ?
                    WHERE run_id = ?
                    """);
        }

        boolean isDuplicate(String eventId) throws SQLException {

            duplicateStatement.setString(1, eventId);

            try (ResultSet rs =
                         duplicateStatement.executeQuery()) {
                return rs.next();
            }
        }

        OrderState findOldState(int orderId) throws SQLException {

            oldStateStatement.setInt(1, orderId);

            try (ResultSet rs = oldStateStatement.executeQuery()) {

                if (rs.next()) {
                    return new OrderState(
                            rs.getString("business_date"),
                            rs.getInt("product_id")
                    );
                }
            }

            return new OrderState(null, null);
        }

        void validateReferences(Event event) throws SQLException {

            checkReference(
                    customerStatement,
                    event.customerId,
                    "Customer"
            );

            checkReference(
                    productStatement,
                    event.productId,
                    "Product"
            );
        }

        private void checkReference(
                PreparedStatement statement,
                int id,
                String label) throws SQLException {

            statement.setInt(1, id);

            try (ResultSet rs = statement.executeQuery()) {

                if (!rs.next()) {
                    throw new IllegalArgumentException(
                            label + " ID does not exist: " + id);
                }
            }
        }

        void applyEvent(Event event) throws SQLException {

            int latestVersion = 0;
            boolean exists = false;
            boolean active = false;

            transactionSelectStatement.setInt(1, event.orderId);

            try (ResultSet rs =
                         transactionSelectStatement.executeQuery()) {

                if (rs.next()) {
                    exists = true;
                    latestVersion = rs.getInt("latest_version");
                    active = rs.getInt("is_active") == 1;
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

                transactionInsertStatement.setInt(1, event.orderId);
                transactionInsertStatement.setInt(2, event.customerId);
                transactionInsertStatement.setInt(3, event.productId);
                transactionInsertStatement.setInt(4, event.quantity);
                transactionInsertStatement.setDouble(5, event.unitPrice);
                transactionInsertStatement.setString(6, event.businessDate);
                transactionInsertStatement.setInt(7, event.version);
                transactionInsertStatement.setString(8, event.eventId);
                transactionInsertStatement.setString(
                        9, Instant.now().toString());

                transactionInsertStatement.executeUpdate();
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

            if (event.eventType.equals("UPDATE")) {

                transactionUpdateStatement.setInt(1, event.customerId);
                transactionUpdateStatement.setInt(2, event.productId);
                transactionUpdateStatement.setInt(3, event.quantity);
                transactionUpdateStatement.setDouble(4, event.unitPrice);
                transactionUpdateStatement.setString(5, event.businessDate);
                transactionUpdateStatement.setInt(6, event.version);
                transactionUpdateStatement.setString(7, event.eventId);
                transactionUpdateStatement.setString(
                        8, Instant.now().toString());
                transactionUpdateStatement.setInt(9, event.orderId);
                transactionUpdateStatement.setInt(10, latestVersion);

                if (transactionUpdateStatement.executeUpdate() != 1) {
                    throw new SQLException(
                            "Order state update failed.");
                }

            } else {

                transactionReverseStatement.setInt(1, event.version);
                transactionReverseStatement.setString(2, event.eventId);
                transactionReverseStatement.setString(
                        3, Instant.now().toString());
                transactionReverseStatement.setInt(4, event.orderId);
                transactionReverseStatement.setInt(5, latestVersion);

                if (transactionReverseStatement.executeUpdate() != 1) {
                    throw new SQLException(
                            "Order state reversal failed.");
                }
            }
        }

        void insertEventHistory(
                Event event,
                String sourceFile,
                int sourceLine) throws SQLException {

            historyStatement.setString(1, event.eventId);
            historyStatement.setInt(2, event.orderId);
            historyStatement.setString(3, event.eventType);
            historyStatement.setInt(4, event.version);
            historyStatement.setInt(5, event.customerId);
            historyStatement.setInt(6, event.productId);
            historyStatement.setInt(7, event.quantity);
            historyStatement.setDouble(8, event.unitPrice);
            historyStatement.setString(9, event.businessDate);
            historyStatement.setString(10, event.eventTimestamp);
            historyStatement.setString(11, sourceFile);
            historyStatement.setInt(12, sourceLine);
            historyStatement.setString(
                    13, Instant.now().toString());

            historyStatement.executeUpdate();
        }

        void markEventProcessed(
                String eventId,
                String runId) throws SQLException {

            processedStatement.setString(1, eventId);
            processedStatement.setString(2, runId);
            processedStatement.setString(
                    3, Instant.now().toString());

            processedStatement.executeUpdate();
        }

        void insertRejectedRecord(
                String runId,
                String sourceFile,
                int sourceLine,
                String rawRecord,
                String error) throws SQLException {

            rejectedStatement.setString(1, runId);
            rejectedStatement.setString(2, sourceFile);
            rejectedStatement.setInt(3, sourceLine);
            rejectedStatement.setString(4, rawRecord);
            rejectedStatement.setString(
                    5, error == null ? "Invalid record" : error);
            rejectedStatement.setString(
                    6, Instant.now().toString());

            rejectedStatement.executeUpdate();
        }

        void updateRun(
                String runId,
                String status,
                int input,
                int accepted,
                int rejected,
                int duplicates,
                String error) throws SQLException {

            updateRunStatement.setString(
                    1, Instant.now().toString());
            updateRunStatement.setString(2, status);
            updateRunStatement.setInt(3, input);
            updateRunStatement.setInt(4, accepted);
            updateRunStatement.setInt(5, rejected);
            updateRunStatement.setInt(6, duplicates);
            updateRunStatement.setString(7, error);
            updateRunStatement.setString(8, runId);

            updateRunStatement.executeUpdate();
        }

        @Override
        public void close() throws SQLException {

            SQLException failure = null;

            PreparedStatement[] statements = {
                    duplicateStatement,
                    oldStateStatement,
                    customerStatement,
                    productStatement,
                    transactionSelectStatement,
                    transactionInsertStatement,
                    transactionUpdateStatement,
                    transactionReverseStatement,
                    historyStatement,
                    processedStatement,
                    rejectedStatement,
                    updateRunStatement
            };

            for (PreparedStatement statement : statements) {

                try {
                    statement.close();

                } catch (SQLException e) {

                    if (failure == null) {
                        failure = e;
                    } else {
                        failure.addSuppressed(e);
                    }
                }
            }

            if (failure != null) {
                throw failure;
            }
        }
    }

    // =====================================================
    // ORDER STATE
    // =====================================================

    private static class OrderState {

        String businessDate;
        Integer productId;

        OrderState(String businessDate, Integer productId) {
            this.businessDate = businessDate;
            this.productId = productId;
        }
    }

    // =====================================================
    // CSV PARSER
    // =====================================================

    private static List<String> parseCsvLine(String line) {

        List<String> values = new ArrayList<>();
        StringBuilder field = new StringBuilder();

        boolean insideQuotes = false;
        boolean quoteClosed = false;

        for (int i = 0; i < line.length(); i++) {

            char current = line.charAt(i);

            if (insideQuotes) {

                if (current == '"') {

                    if (i + 1 < line.length()
                            && line.charAt(i + 1) == '"') {

                        field.append('"');
                        i++;

                    } else {
                        insideQuotes = false;
                        quoteClosed = true;
                    }

                } else {
                    field.append(current);
                }

            } else {

                if (quoteClosed) {

                    if (current == ',') {
                        values.add(field.toString());
                        field.setLength(0);
                        quoteClosed = false;

                    } else if (!Character.isWhitespace(current)) {
                        throw new IllegalArgumentException(
                                "Unexpected character after closing quote.");
                    }

                } else {

                    if (current == ',') {

                        values.add(field.toString());
                        field.setLength(0);

                    } else if (current == '"') {

                        if (field.toString().trim().isEmpty()) {
                            field.setLength(0);
                            insideQuotes = true;
                        } else {
                            throw new IllegalArgumentException(
                                    "Unexpected quotation mark in unquoted field.");
                        }

                    } else {
                        field.append(current);
                    }
                }
            }
        }

        if (insideQuotes) {
            throw new IllegalArgumentException(
                    "CSV contains an unterminated quoted field.");
        }

        values.add(field.toString());

        return values;
    }

    // =====================================================
    // VALIDATION AND EVENT PARSING
    // =====================================================

    private static void validateRequiredFields(String[] values) {

        String[] fieldNames = {
                "event_id", "order_id", "event_type",
                "event_version", "customer_id", "product_id",
                "quantity", "unit_price", "business_date",
                "event_timestamp"
        };

        for (int i = 0; i < values.length; i++) {

            if (values[i] == null || values[i].trim().isEmpty()) {
                throw new IllegalArgumentException(
                        "Required field is missing: " + fieldNames[i]);
            }
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

        LocalDate businessDate =
                LocalDate.parse(v[8].trim());

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

        if (!Double.isFinite(unitPrice) || unitPrice < 0) {
            throw new IllegalArgumentException(
                    "Unit price must be a finite, non-negative number.");
        }

        return new Event(
                eventId,
                orderId,
                eventType,
                version,
                customerId,
                productId,
                quantity,
                unitPrice,
                businessDate.toString(),
                eventTimestamp.toString()
        );
    }

    // =====================================================
    // DATABASE HELPERS
    // =====================================================

    private static void enableForeignKeys(Connection connection)
            throws SQLException {

        try (PreparedStatement ps =
                     connection.prepareStatement(
                             "PRAGMA foreign_keys = ON")) {
            ps.execute();
        }
    }

    private static void createPipelineRun(
            String runId,
            String sourceFile) throws SQLException {

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

    private static void updateFailedRun(
            String runId,
            String error) {

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

    // =====================================================
    // EVENT MODEL
    // =====================================================

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

        Event(
                String eventId,
                int orderId,
                String eventType,
                int version,
                int customerId,
                int productId,
                int quantity,
                double unitPrice,
                String businessDate,
                String eventTimestamp) {

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
