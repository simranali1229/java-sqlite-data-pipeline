
package com.example;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class EventProcessorTest {

    private Path testDatabase;
    private Path csvFile;

    private static final String HEADER =
            "event_id,order_id,event_type,event_version,customer_id," +
            "product_id,quantity,unit_price,business_date,event_timestamp";

    @Before
    public void setUp() throws Exception {

        testDatabase = Files.createTempFile("event-test-", ".db");

        System.setProperty(
                "db.url",
                "jdbc:sqlite:" + testDatabase.toAbsolutePath()
        );

        DatabaseSetup.createTables();
        DataSeeder.insertSampleData();
        DatabaseSetup.createTables();
    }

    @After
    public void tearDown() throws Exception {

        System.clearProperty("db.url");

        if (csvFile != null) {
            Files.deleteIfExists(csvFile);
        }

        if (testDatabase != null) {
            Files.deleteIfExists(testDatabase);
        }
    }

    // TEST 1: CREATE a new order
    @Test
    public void testCreateEvent() throws Exception {

        processEvents(
                "EVT_TEST_001,9,CREATE,1,1,2,2,20000," +
                "2026-11-14,2026-11-14T21:00:00"
        );

        assertEquals(1, queryInt(
                "SELECT COUNT(*) FROM transaction_state WHERE order_id = 9"
        ));

        assertEquals(1, queryInt(
                "SELECT COUNT(*) FROM event_history " +
                "WHERE event_id = 'EVT_TEST_001'"
        ));

        assertEquals(1, queryInt(
                "SELECT COUNT(*) FROM processed_events " +
                "WHERE event_id = 'EVT_TEST_001'"
        ));
    }

    // TEST 2: UPDATE an existing order
    @Test
    public void testUpdateEvent() throws Exception {

        processEvents(
                "EVT_TEST_002,3,UPDATE,1,3,3,2,1500," +
                "2026-11-11,2026-11-14T21:01:00"
        );

        assertEquals(2, queryInt(
                "SELECT quantity FROM transaction_state WHERE order_id = 3"
        ));

        assertEquals(1, queryInt(
                "SELECT latest_version FROM transaction_state " +
                "WHERE order_id = 3"
        ));

        assertEquals(1, queryInt(
                "SELECT COUNT(*) FROM event_history " +
                "WHERE event_id = 'EVT_TEST_002'"
        ));
    }

    // TEST 3: REVERSE an existing order
    @Test
    public void testReverseEvent() throws Exception {

        processEvents(
                "EVT_TEST_003,8,REVERSE,1,3,1,1,55000," +
                "2026-11-13,2026-11-14T21:02:00"
        );

        assertEquals(0, queryInt(
                "SELECT is_active FROM transaction_state WHERE order_id = 8"
        ));

        assertEquals(1, queryInt(
                "SELECT latest_version FROM transaction_state " +
                "WHERE order_id = 8"
        ));
    }

    // TEST 4: Duplicate event should not be processed twice
    @Test
    public void testDuplicateEvent() throws Exception {

        String event =
                "EVT_TEST_004,9,CREATE,1,1,2,2,20000," +
                "2026-11-14,2026-11-14T21:03:00";

        processEvents(event);
        processEvents(event);

        assertEquals(1, queryInt(
                "SELECT COUNT(*) FROM processed_events " +
                "WHERE event_id = 'EVT_TEST_004'"
        ));

        assertEquals(1, queryInt(
                "SELECT COUNT(*) FROM event_history " +
                "WHERE event_id = 'EVT_TEST_004'"
        ));

        assertEquals(1, queryInt(
                "SELECT duplicate_count FROM pipeline_runs " +
                "ORDER BY started_at DESC LIMIT 1"
        ));
    }

    // TEST 5: Invalid customer should be rejected
    @Test
    public void testInvalidCustomerRejected() throws Exception {

        processEvents(
                "EVT_TEST_005,9,CREATE,1,999,2,2,20000," +
                "2026-11-14,2026-11-14T21:04:00"
        );

        assertEquals(1, queryInt(
                "SELECT COUNT(*) FROM rejected_records"
        ));

        assertEquals(0, queryInt(
                "SELECT COUNT(*) FROM processed_events " +
                "WHERE event_id = 'EVT_TEST_005'"
        ));
    }

    // TEST 6: Incorrect event version should be rejected
    @Test
    public void testIncorrectVersionRejected() throws Exception {

        processEvents(
                "EVT_TEST_006,3,UPDATE,2,3,3,2,1500," +
                "2026-11-11,2026-11-14T21:05:00"
        );

        assertEquals(1, queryInt(
                "SELECT COUNT(*) FROM rejected_records"
        ));

        assertEquals(0, queryInt(
                "SELECT latest_version FROM transaction_state " +
                "WHERE order_id = 3"
        ));
    }

    // TEST 7: Empty CSV file should fail
    @Test(expected = IllegalArgumentException.class)
    public void testEmptyCsvFile() throws Exception {

        csvFile = Files.createTempFile("empty-events-", ".csv");

        EventProcessor.processCsv(csvFile.toAbsolutePath().toString());
    }

    // TEST 8: Incorrect CSV header should fail
    @Test(expected = IllegalArgumentException.class)
    public void testIncorrectHeader() throws Exception {

        csvFile = Files.createTempFile("wrong-header-", ".csv");

        Files.writeString(
                csvFile,
                "id,order,type,version\n"
        );

        EventProcessor.processCsv(csvFile.toAbsolutePath().toString());
    }

    // TEST 9: Missing columns should be rejected
    @Test
    public void testMissingColumnsRejected() throws Exception {

        processEvents(
                "EVT_TEST_007,9,CREATE,1,1,2,2"
        );

        assertEquals(1, queryInt(
                "SELECT COUNT(*) FROM rejected_records"
        ));

        assertEquals(0, queryInt(
                "SELECT COUNT(*) FROM processed_events"
        ));
    }

    // TEST 10: Zero quantity should be rejected
    @Test
    public void testZeroQuantityRejected() throws Exception {

        processEvents(
                "EVT_TEST_008,9,CREATE,1,1,2,0,20000," +
                "2026-11-14,2026-11-14T21:10:00"
        );

        assertEquals(1, queryInt(
                "SELECT COUNT(*) FROM rejected_records"
        ));

        assertEquals(0, queryInt(
                "SELECT COUNT(*) FROM transaction_state WHERE order_id = 9"
        ));
    }

    // TEST 11: Negative price should be rejected
    @Test
    public void testNegativePriceRejected() throws Exception {

        processEvents(
                "EVT_TEST_009,9,CREATE,1,1,2,2,-20000," +
                "2026-11-14,2026-11-14T21:11:00"
        );

        assertEquals(1, queryInt(
                "SELECT COUNT(*) FROM rejected_records"
        ));
    }

    // TEST 12: Invalid date should be rejected
    @Test
    public void testInvalidDateRejected() throws Exception {

        processEvents(
                "EVT_TEST_010,9,CREATE,1,1,2,2,20000," +
                "invalid-date,2026-11-14T21:12:00"
        );

        assertEquals(1, queryInt(
                "SELECT COUNT(*) FROM rejected_records"
        ));
    }

    // TEST 13: Duplicate order CREATE should be rejected
    @Test
    public void testDuplicateOrderCreateRejected() throws Exception {

        processEvents(
                "EVT_TEST_011,9,CREATE,1,1,2,2,20000," +
                "2026-11-14,2026-11-14T21:13:00"
        );

        processEvents(
                "EVT_TEST_012,9,CREATE,1,1,2,2,20000," +
                "2026-11-14,2026-11-14T21:14:00"
        );

        assertEquals(1, queryInt(
                "SELECT COUNT(*) FROM transaction_state WHERE order_id = 9"
        ));

        assertEquals(1, queryInt(
                "SELECT COUNT(*) FROM rejected_records"
        ));
    }

    // TEST 14: Updating a reversed order should be rejected
    @Test
    public void testUpdateReversedOrderRejected() throws Exception {

        processEvents(
                "EVT_TEST_013,8,REVERSE,1,3,1,1,55000," +
                "2026-11-13,2026-11-14T21:15:00"
        );

        processEvents(
                "EVT_TEST_014,8,UPDATE,2,3,1,2,55000," +
                "2026-11-13,2026-11-14T21:16:00"
        );

        assertEquals(0, queryInt(
                "SELECT is_active FROM transaction_state WHERE order_id = 8"
        ));

        assertEquals(1, queryInt(
                "SELECT COUNT(*) FROM rejected_records"
        ));
    }

    // TEST 15: CREATE should update daily product metrics
    @Test
    public void testCreateUpdatesDailyMetrics() throws Exception {

        processEvents(
                "EVT_METRIC_001,9,CREATE,1,1,2,2,20000," +
                "2026-11-14,2026-11-14T21:20:00"
        );

        assertEquals(1, queryInt(
                "SELECT order_count FROM daily_product_metrics " +
                "WHERE business_date = '2026-11-14' AND product_id = 2"
        ));

        assertEquals(2, queryInt(
                "SELECT total_quantity FROM daily_product_metrics " +
                "WHERE business_date = '2026-11-14' AND product_id = 2"
        ));

        assertEquals(40000.0, queryDouble(
                "SELECT total_revenue FROM daily_product_metrics " +
                "WHERE business_date = '2026-11-14' AND product_id = 2"
        ), 0.01);
    }

    // TEST 16: UPDATE quantity and price should refresh metrics
    @Test
    public void testUpdateRefreshesDailyMetrics() throws Exception {

        processEvents(
                "EVT_METRIC_002,3,UPDATE,1,3,3,2,1500," +
                "2026-11-11,2026-11-14T21:21:00"
        );

        assertEquals(1, queryInt(
                "SELECT order_count FROM daily_product_metrics " +
                "WHERE business_date = '2026-11-11' AND product_id = 3"
        ));

        assertEquals(2, queryInt(
                "SELECT total_quantity FROM daily_product_metrics " +
                "WHERE business_date = '2026-11-11' AND product_id = 3"
        ));

        assertEquals(3000.0, queryDouble(
                "SELECT total_revenue FROM daily_product_metrics " +
                "WHERE business_date = '2026-11-11' AND product_id = 3"
        ), 0.01);
    }

    // TEST 17: UPDATE date should refresh old and new dates
    @Test
    public void testUpdateBusinessDateRefreshesMetrics() throws Exception {

        processEvents(
                "EVT_METRIC_003,3,UPDATE,1,3,3,2,1500," +
                "2026-11-15,2026-11-14T21:22:00"
        );

        assertEquals(0, queryInt(
                "SELECT COUNT(*) FROM daily_product_metrics " +
                "WHERE business_date = '2026-11-11' AND product_id = 3"
        ));

        assertEquals(1, queryInt(
                "SELECT order_count FROM daily_product_metrics " +
                "WHERE business_date = '2026-11-15' AND product_id = 3"
        ));

        assertEquals(2, queryInt(
                "SELECT total_quantity FROM daily_product_metrics " +
                "WHERE business_date = '2026-11-15' AND product_id = 3"
        ));

        assertEquals(3000.0, queryDouble(
                "SELECT total_revenue FROM daily_product_metrics " +
                "WHERE business_date = '2026-11-15' AND product_id = 3"
        ), 0.01);
    }

    // TEST 18: UPDATE product should refresh old and new products
    @Test
    public void testUpdateProductRefreshesMetrics() throws Exception {

        processEvents(
                "EVT_METRIC_004,3,UPDATE,1,3,4,2,1500," +
                "2026-11-11,2026-11-14T21:23:00"
        );

        assertEquals(0, queryInt(
                "SELECT COUNT(*) FROM daily_product_metrics " +
                "WHERE business_date = '2026-11-11' AND product_id = 3"
        ));

        assertEquals(2, queryInt(
                "SELECT order_count FROM daily_product_metrics " +
                "WHERE business_date = '2026-11-11' AND product_id = 4"
        ));

        assertEquals(4, queryInt(
                "SELECT total_quantity FROM daily_product_metrics " +
                "WHERE business_date = '2026-11-11' AND product_id = 4"
        ));

        assertEquals(5400.0, queryDouble(
                "SELECT total_revenue FROM daily_product_metrics " +
                "WHERE business_date = '2026-11-11' AND product_id = 4"
        ), 0.01);
    }

    // TEST 19: REVERSE should remove order from daily metrics
    @Test
    public void testReverseRefreshesDailyMetrics() throws Exception {

        processEvents(
                "EVT_METRIC_005,8,REVERSE,1,3,1,1,55000," +
                "2026-11-13,2026-11-14T21:24:00"
        );

        assertEquals(0, queryInt(
                "SELECT COUNT(*) FROM daily_product_metrics " +
                "WHERE business_date = '2026-11-13' AND product_id = 1"
        ));
    }

    // TEST 20: Rejected event must not change daily metrics
    @Test
    public void testRejectedEventDoesNotChangeDailyMetrics()
            throws Exception {

        double revenueBefore = queryDouble(
                "SELECT total_revenue FROM daily_product_metrics " +
                "WHERE business_date = '2026-11-11' AND product_id = 3"
        );

        processEvents(
                "EVT_METRIC_006,9,CREATE,1,999,3,2,1500," +
                "2026-11-11,2026-11-14T21:25:00"
        );

        double revenueAfter = queryDouble(
                "SELECT total_revenue FROM daily_product_metrics " +
                "WHERE business_date = '2026-11-11' AND product_id = 3"
        );

        assertEquals(revenueBefore, revenueAfter, 0.01);

        assertEquals(1, queryInt(
                "SELECT COUNT(*) FROM rejected_records"
        ));
    }

    // TEST 21: Quoted comma inside event ID should be accepted
    @Test
    public void testQuotedCommaInEventId() throws Exception {

        processEvents(
                "\"EVT,CSV,001\",9,CREATE,1,1,2,2,20000," +
                "2026-11-14,2026-11-14T21:30:00"
        );

        assertEquals(1, queryInt(
                "SELECT COUNT(*) FROM processed_events " +
                "WHERE event_id = 'EVT,CSV,001'"
        ));
    }

    // TEST 22: Escaped double quotes should be parsed correctly
    @Test
    public void testEscapedQuotesInEventId() throws Exception {

        processEvents(
                "\"EVT\"\"CSV\"\"002\",9,CREATE,1,1,2,2,20000," +
                "2026-11-14,2026-11-14T21:31:00"
        );

        assertEquals(1, queryInt(
                "SELECT COUNT(*) FROM processed_events " +
                "WHERE event_id = 'EVT\"CSV\"002'"
        ));
    }

    // TEST 23: Unclosed quotation mark should be rejected
    @Test
    public void testUnclosedQuoteRejected() throws Exception {

        processEvents(
                "\"EVT_MALFORMED,9,CREATE,1,1,2,2,20000," +
                "2026-11-14,2026-11-14T21:32:00"
        );

        assertEquals(1, queryInt(
                "SELECT COUNT(*) FROM rejected_records"
        ));

        assertEquals(0, queryInt(
                "SELECT COUNT(*) FROM processed_events"
        ));
    }

    // TEST 24: Extra columns should be rejected
    @Test
    public void testExtraColumnsRejected() throws Exception {

        processEvents(
                "EVT_EXTRA,9,CREATE,1,1,2,2,20000," +
                "2026-11-14,2026-11-14T21:33:00,EXTRA"
        );

        assertEquals(1, queryInt(
                "SELECT COUNT(*) FROM rejected_records"
        ));

        assertEquals(0, queryInt(
                "SELECT COUNT(*) FROM processed_events"
        ));
    }

    // TEST 25: Empty required field should be rejected
    @Test
    public void testEmptyRequiredFieldRejected() throws Exception {

        processEvents(
                ",9,CREATE,1,1,2,2,20000," +
                "2026-11-14,2026-11-14T21:34:00"
        );

        assertEquals(1, queryInt(
                "SELECT COUNT(*) FROM rejected_records"
        ));

        assertEquals(0, queryInt(
                "SELECT COUNT(*) FROM processed_events"
        ));
    }

    // TEST 26: UTF-8 BOM in header should be supported
    @Test
    public void testUtf8BomHeader() throws Exception {

        csvFile = Files.createTempFile("bom-events-", ".csv");

        String record =
                "EVT_BOM,9,CREATE,1,1,2,2,20000," +
                "2026-11-14,2026-11-14T21:35:00";

        Files.writeString(
                csvFile,
                "\uFEFF" + HEADER + "\n" + record + "\n"
        );

        EventProcessor.processCsv(csvFile.toAbsolutePath().toString());

        assertEquals(1, queryInt(
                "SELECT COUNT(*) FROM processed_events " +
                "WHERE event_id = 'EVT_BOM'"
        ));
    }

    // TEST 27: Out-of-order event recovery through manual replay
    @Test
    public void testOutOfOrderEventRecovery() throws Exception {

        // Version 2 arrives before version 1.
        processEvents(
                "EVT_ORDER_3_V2,3,UPDATE,2,3,3,5,1500," +
                "2026-11-11,2026-11-15T10:00:00"
        );

        assertEquals(1, queryInt(
                "SELECT COUNT(*) FROM rejected_records"
        ));

        assertEquals(0, queryInt(
                "SELECT latest_version FROM transaction_state " +
                "WHERE order_id = 3"
        ));

        // The missing version 1 arrives.
        processEvents(
                "EVT_ORDER_3_V1,3,UPDATE,1,3,3,2,1500," +
                "2026-11-11,2026-11-15T10:01:00"
        );

        assertEquals(1, queryInt(
                "SELECT latest_version FROM transaction_state " +
                "WHERE order_id = 3"
        ));

        // Replay version 2 after version 1 is accepted.
        processEvents(
                "EVT_ORDER_3_V2,3,UPDATE,2,3,3,5,1500," +
                "2026-11-11,2026-11-15T10:02:00"
        );

        assertEquals(2, queryInt(
                "SELECT latest_version FROM transaction_state " +
                "WHERE order_id = 3"
        ));

        assertEquals(5, queryInt(
                "SELECT quantity FROM transaction_state " +
                "WHERE order_id = 3"
        ));

        assertEquals(1, queryInt(
                "SELECT COUNT(*) FROM event_history " +
                "WHERE event_id = 'EVT_ORDER_3_V2'"
        ));
    }

    // TEST 28: Older event must not overwrite newer order state
    @Test
    public void testOlderVersionDoesNotChangeOrderState()
            throws Exception {

        processEvents(
                "EVT_ORDER_3_V1,3,UPDATE,1,3,3,2,1500," +
                "2026-11-11,2026-11-15T10:03:00"
        );

        processEvents(
                "EVT_ORDER_3_V2,3,UPDATE,2,3,3,5,1500," +
                "2026-11-11,2026-11-15T10:04:00"
        );

        // Submit an outdated version.
        processEvents(
                "EVT_ORDER_3_OLD,3,UPDATE,1,3,3,9,1500," +
                "2026-11-11,2026-11-15T10:05:00"
        );

        assertEquals(2, queryInt(
                "SELECT latest_version FROM transaction_state " +
                "WHERE order_id = 3"
        ));

        assertEquals(5, queryInt(
                "SELECT quantity FROM transaction_state " +
                "WHERE order_id = 3"
        ));

        assertEquals(1, queryInt(
                "SELECT COUNT(*) FROM rejected_records"
        ));
    }

    // TEST 29: Late-arriving event must update historical metrics
    @Test
    public void testLateArrivingEventUpdatesHistoricalMetrics()
            throws Exception {

        String historicalDate = "2025-01-10";

        int quantityBefore = queryInt(
                "SELECT COALESCE(SUM(total_quantity), 0) " +
                "FROM daily_product_metrics " +
                "WHERE business_date = '" + historicalDate + "' " +
                "AND product_id = 2"
        );

        double revenueBefore = queryDouble(
                "SELECT COALESCE(SUM(total_revenue), 0) " +
                "FROM daily_product_metrics " +
                "WHERE business_date = '" + historicalDate + "' " +
                "AND product_id = 2"
        );

        // Event arrives on 15 November but belongs to 10 January.
        processEvents(
                "EVT_LATE_001,9,CREATE,1,1,2,2,20000," +
                "2025-01-10,2026-11-15T10:06:00"
        );

        assertEquals(1, queryInt(
                "SELECT COUNT(*) FROM processed_events " +
                "WHERE event_id = 'EVT_LATE_001'"
        ));

        assertEquals(quantityBefore + 2, queryInt(
                "SELECT COALESCE(SUM(total_quantity), 0) " +
                "FROM daily_product_metrics " +
                "WHERE business_date = '" + historicalDate + "' " +
                "AND product_id = 2"
        ));

        assertEquals(
                revenueBefore + 40000.0,
                queryDouble(
                        "SELECT COALESCE(SUM(total_revenue), 0) " +
                        "FROM daily_product_metrics " +
                        "WHERE business_date = '" + historicalDate + "' " +
                        "AND product_id = 2"
                ),
                0.01
        );
    }

    // Helper: create CSV and process its events
    private void processEvents(String... records) throws Exception {

        if (csvFile != null) {
            Files.deleteIfExists(csvFile);
        }

        List<String> lines = new ArrayList<>();
        lines.add(HEADER);

        for (String record : records) {
            lines.add(record);
        }

        csvFile = Files.createTempFile("test-events-", ".csv");

        Files.write(csvFile, lines);

        EventProcessor.processCsv(csvFile.toAbsolutePath().toString());
    }

    // Helper: execute an integer query
    private int queryInt(String sql) throws Exception {

        try (Connection connection = DatabaseConnection.connect();
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(sql)) {

            if (rs.next()) {
                return rs.getInt(1);
            }

            return 0;
        }
    }

    // Helper: execute a decimal query
    private double queryDouble(String sql) throws Exception {

        try (Connection connection = DatabaseConnection.connect();
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(sql)) {

            if (rs.next()) {
                return rs.getDouble(1);
            }

            return 0.0;
        }
    }
}
