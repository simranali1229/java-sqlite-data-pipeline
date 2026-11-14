package com.example;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
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

        // Create database tables
        DatabaseSetup.createTables();

        // Insert sample products, customers and orders
        DataSeeder.insertSampleData();

        // Migrate seeded orders into transaction_state
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

    // Helper: create CSV and process its events
    private void processEvents(String... records) throws Exception {

        List<String> lines = new ArrayList<>();
        lines.add(HEADER);

        for (String record : records) {
            lines.add(record);
        }

        csvFile = Files.createTempFile("test-events-", ".csv");

        Files.write(csvFile, lines);

        EventProcessor.processCsv(csvFile.toAbsolutePath().toString());
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


    // Helper: execute a SELECT COUNT or single-value query
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
}
