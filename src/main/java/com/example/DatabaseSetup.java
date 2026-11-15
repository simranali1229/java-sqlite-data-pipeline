
package com.example;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public class DatabaseSetup {

    public static void createTables() {

        try (Connection connection = DatabaseConnection.connect();
             Statement statement = connection.createStatement()) {

            // Enable foreign key validation
            statement.execute("PRAGMA foreign_keys = ON");

            connection.setAutoCommit(false);

            try {

                // 1. PRODUCTS TABLE
                String productsTable = """
                    CREATE TABLE IF NOT EXISTS products (
                        product_id INTEGER PRIMARY KEY AUTOINCREMENT,
                        product_name TEXT NOT NULL,
                        category TEXT,
                        price REAL NOT NULL CHECK(price >= 0)
                    )
                    """;

                // 2. CUSTOMERS TABLE
                String customersTable = """
                    CREATE TABLE IF NOT EXISTS customers (
                        customer_id INTEGER PRIMARY KEY AUTOINCREMENT,
                        customer_name TEXT NOT NULL,
                        email TEXT UNIQUE,
                        city TEXT
                    )
                    """;

                // 3. ORDERS TABLE
                String ordersTable = """
                    CREATE TABLE IF NOT EXISTS orders (
                        order_id INTEGER PRIMARY KEY AUTOINCREMENT,
                        customer_id INTEGER NOT NULL,
                        product_id INTEGER NOT NULL,
                        quantity INTEGER NOT NULL CHECK(quantity > 0),
                        unit_price REAL NOT NULL,
                        order_date TEXT NOT NULL,
                        FOREIGN KEY(customer_id) REFERENCES customers(customer_id),
                        FOREIGN KEY(product_id) REFERENCES products(product_id)
                    )
                    """;

                // 4. PIPELINE RUNS TABLE
                String pipelineRunsTable = """
                    CREATE TABLE IF NOT EXISTS pipeline_runs (
                        run_id TEXT PRIMARY KEY,
                        source_file TEXT,
                        started_at TEXT NOT NULL,
                        finished_at TEXT,
                        status TEXT NOT NULL
                            CHECK(status IN ('RUNNING', 'SUCCESS', 'FAILED')),
                        input_count INTEGER DEFAULT 0,
                        accepted_count INTEGER DEFAULT 0,
                        rejected_count INTEGER DEFAULT 0,
                        duplicate_count INTEGER DEFAULT 0,
                        error_message TEXT
                    )
                    """;

                // 5. TRANSACTION STATE TABLE
                String transactionStateTable = """
                    CREATE TABLE IF NOT EXISTS transaction_state (
                        order_id INTEGER PRIMARY KEY,
                        customer_id INTEGER NOT NULL,
                        product_id INTEGER NOT NULL,
                        quantity INTEGER NOT NULL CHECK(quantity > 0),
                        unit_price REAL NOT NULL CHECK(unit_price >= 0),
                        business_date TEXT NOT NULL,
                        latest_version INTEGER NOT NULL DEFAULT 0,
                        is_active INTEGER NOT NULL DEFAULT 1
                            CHECK(is_active IN (0, 1)),
                        last_event_id TEXT NOT NULL,
                        updated_at TEXT NOT NULL,
                        FOREIGN KEY(customer_id) REFERENCES customers(customer_id),
                        FOREIGN KEY(product_id) REFERENCES products(product_id)
                    )
                    """;

                // 6. EVENT HISTORY TABLE
                String eventHistoryTable = """
                    CREATE TABLE IF NOT EXISTS event_history (
                        event_id TEXT PRIMARY KEY,
                        order_id INTEGER NOT NULL,
                        event_type TEXT NOT NULL
                            CHECK(event_type IN ('CREATE', 'UPDATE', 'REVERSE')),
                        event_version INTEGER NOT NULL CHECK(event_version > 0),
                        customer_id INTEGER,
                        product_id INTEGER,
                        quantity INTEGER,
                        unit_price REAL,
                        business_date TEXT,
                        event_timestamp TEXT NOT NULL,
                        source_file TEXT,
                        source_line INTEGER,
                        processed_at TEXT NOT NULL,
                        UNIQUE(order_id, event_version),
                        FOREIGN KEY(customer_id) REFERENCES customers(customer_id),
                        FOREIGN KEY(product_id) REFERENCES products(product_id)
                    )
                    """;

                // 7. PROCESSED EVENTS TABLE
                String processedEventsTable = """
                    CREATE TABLE IF NOT EXISTS processed_events (
                        event_id TEXT PRIMARY KEY,
                        run_id TEXT NOT NULL,
                        processed_at TEXT NOT NULL,
                        FOREIGN KEY(run_id) REFERENCES pipeline_runs(run_id)
                    )
                    """;

                // 8. REJECTED RECORDS TABLE
                String rejectedRecordsTable = """
                    CREATE TABLE IF NOT EXISTS rejected_records (
                        reject_id INTEGER PRIMARY KEY AUTOINCREMENT,
                        run_id TEXT NOT NULL,
                        source_file TEXT,
                        source_line INTEGER,
                        raw_record TEXT,
                        error_message TEXT NOT NULL,
                        rejected_at TEXT NOT NULL,
                        FOREIGN KEY(run_id) REFERENCES pipeline_runs(run_id)
                    )
                    """;

                // 9. DAILY PRODUCT METRICS TABLE
                String dailyProductMetricsTable = """
                    CREATE TABLE IF NOT EXISTS daily_product_metrics (
                        business_date TEXT NOT NULL,
                        product_id INTEGER NOT NULL,
                        order_count INTEGER NOT NULL DEFAULT 0,
                        total_quantity INTEGER NOT NULL DEFAULT 0,
                        total_revenue REAL NOT NULL DEFAULT 0,
                        updated_at TEXT NOT NULL,
                        PRIMARY KEY (business_date, product_id),
                        FOREIGN KEY(product_id) REFERENCES products(product_id)
                    )
                    """;

                // Create existing tables
                statement.execute(productsTable);
                statement.execute(customersTable);
                statement.execute(ordersTable);

                // Create pipeline tables
                statement.execute(pipelineRunsTable);
                statement.execute(transactionStateTable);
                statement.execute(eventHistoryTable);
                statement.execute(processedEventsTable);
                statement.execute(rejectedRecordsTable);

                // Create daily metrics table
                statement.execute(dailyProductMetricsTable);

                // Create indexes for faster queries
                statement.execute("""
                    CREATE INDEX IF NOT EXISTS idx_state_business_date
                    ON transaction_state(business_date)
                    """);

                statement.execute("""
                    CREATE INDEX IF NOT EXISTS idx_history_order
                    ON event_history(order_id)
                    """);

                statement.execute("""
                    CREATE INDEX IF NOT EXISTS idx_daily_metrics_product
                    ON daily_product_metrics(product_id)
                    """);

                // Migrate existing orders into transaction state.
                // Version 0 represents original baseline data.
                statement.execute("""
                    INSERT OR IGNORE INTO transaction_state (
                        order_id,
                        customer_id,
                        product_id,
                        quantity,
                        unit_price,
                        business_date,
                        latest_version,
                        is_active,
                        last_event_id,
                        updated_at
                    )
                    SELECT
                        order_id,
                        customer_id,
                        product_id,
                        quantity,
                        unit_price,
                        order_date,
                        0,
                        1,
                        'BASELINE-' || order_id,
                        CURRENT_TIMESTAMP
                    FROM orders
                    """);

                // Refresh daily metrics from current active orders.
                // This also rebuilds the summary when setup runs again.
                statement.executeUpdate(
                    "DELETE FROM daily_product_metrics"
                );

                statement.executeUpdate("""
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
                        CURRENT_TIMESTAMP
                    FROM transaction_state
                    WHERE is_active = 1
                    GROUP BY business_date, product_id
                    """);

                // Commit all database changes
                connection.commit();

                System.out.println("==================================");
                System.out.println("DATABASE SETUP COMPLETED");
                System.out.println("==================================");
                System.out.println("Products table ready!");
                System.out.println("Customers table ready!");
                System.out.println("Orders table ready!");
                System.out.println("Pipeline runs table ready!");
                System.out.println("Transaction state table ready!");
                System.out.println("Event history table ready!");
                System.out.println("Processed events table ready!");
                System.out.println("Rejected records table ready!");
                System.out.println("Daily product metrics table ready!");
                System.out.println("Existing orders migrated!");
                System.out.println("Daily metrics refreshed!");

            } catch (SQLException e) {

                // Roll back if any database operation fails
                try {
                    connection.rollback();
                } catch (SQLException rollbackException) {
                    e.addSuppressed(rollbackException);
                }

                throw e;
            }

        } catch (SQLException e) {

            // Propagate the error instead of silently continuing
            throw new IllegalStateException(
                "Database setup failed. Please check the database connection and SQL statements.",
                e
            );
        }
    }
}
