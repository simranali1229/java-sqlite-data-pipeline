package com.example;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.Assert.assertEquals;

public class DataSeederTest {

    private Path testDatabase;

    @Before
    public void setUp() throws Exception {

        // Create a separate temporary database for testing
        testDatabase = Files.createTempFile("pipeline-test-", ".db");

        System.setProperty(
                "db.url",
                "jdbc:sqlite:" + testDatabase.toAbsolutePath()
        );

        // Step 1: Create all database tables
        DatabaseSetup.createTables();

        // Step 2: Insert sample data
        DataSeeder.insertSampleData();

        // Step 3: Migrate seeded orders into transaction_state
        DatabaseSetup.createTables();
    }

    @After
    public void tearDown() throws Exception {

        System.clearProperty("db.url");

        if (testDatabase != null) {
            Files.deleteIfExists(testDatabase);
        }
    }

    @Test
    public void testSampleDataInserted() throws Exception {

        assertEquals(5, countRows("products"));
        assertEquals(5, countRows("customers"));
        assertEquals(8, countRows("orders"));
        assertEquals(8, countRows("transaction_state"));
    }

    @Test
    public void testDataSeederIsIdempotent() throws Exception {

        // Insert the same sample data again
        DataSeeder.insertSampleData();

        // Counts should remain unchanged
        assertEquals(5, countRows("products"));
        assertEquals(5, countRows("customers"));
        assertEquals(8, countRows("orders"));
        assertEquals(8, countRows("transaction_state"));
    }

    private int countRows(String tableName) throws Exception {

        String sql = "SELECT COUNT(*) FROM " + tableName;

        try (Connection connection = DatabaseConnection.connect();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {

            resultSet.next();
            return resultSet.getInt(1);
        }
    }
}
