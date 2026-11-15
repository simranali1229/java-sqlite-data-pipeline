# Daily Product Metrics Data Engineering Pipeline

## 1. Project Overview

A Java-based data engineering pipeline that processes incremental transaction events from CSV files and maintains daily product metrics using SQLite.

The pipeline supports transaction creation, updates, reversals, duplicate detection, invalid-record tracking, event history, and recomputation of affected business dates and products.

This project was developed as a 72-hour data engineering coding challenge, with a focus on correctness, maintainability, testing, idempotency, and measurable results.

## 2. Business Objective

Maintain accurate daily product metrics from incremental transaction events.

The pipeline must correctly handle:

- New transactions.
- Existing transaction updates.
- Transaction reversals.
- Duplicate events.
- Late-arriving events.
- Invalid or malformed records.
- Recalculation of affected metric groups.
- Safe processing and reconciliation.

**Metrics grain:** One record per business date and product.

## 3. Technology Stack

| Technology | Purpose |
|---|---|
| Java 17 | Application development |
| SQLite | Relational database |
| JDBC | Database connectivity |
| Maven | Build and dependency management |
| JUnit 4 | Automated testing |
| CSV | Transaction event input |

## 4. Architecture

The application follows this processing flow:

```text
             CSV Transaction Events
                       |
                       v
              EventProcessor
                       |
          Parsing and Validation
                       |
          Duplicate and Version Checks
                       |
             Event Processing
                       |
          +------------+------------+
          |            |            |
          v            v            v
    Transaction    Event History  Rejected
       State                      Records
          |
          v
    DailyMetricsService
          |
          v
   Daily Product Metrics
          |
          v
   Reconciliation and Reports
```

### Main processing modules

**EventProcessor**
- Reads transaction events from CSV.
- Validates event fields and business rules.
- Handles CREATE, UPDATE, and REVERSE operations.
- Detects duplicate event IDs.
- Checks transaction versions.
- Records accepted and rejected events.
- Uses database transactions and per-record savepoints.

**DailyMetricsService**
- Recomputes daily product metrics.
- Handles changes to business dates and product IDs.
- Recalculates both old and new metric groups when an order changes.
- Excludes reversed transactions from active metrics.

**DatabaseSetup**
- Creates the required database tables.
- Initializes the database schema.
- Migrates existing orders into transaction state.
- Refreshes daily product metrics.

**DatabaseConnection**
- Manages SQLite connections.
- Enables foreign-key enforcement.
- Supports database URL overrides for isolated testing.

## 5. Database Design and Data Dictionary

The project uses the following tables:

| Table | Purpose |
|---|---|
| `products` | Product reference information |
| `customers` | Customer reference information |
| `orders` | Original/legacy order data used during migration |
| `transaction_state` | Latest state of each transaction |
| `event_history` | Historical transaction events |
| `processed_events` | Tracks successfully processed event IDs |
| `rejected_records` | Stores rejected source records and error information |
| `pipeline_runs` | Stores pipeline execution details and counts |
| `daily_product_metrics` | Stores aggregated daily product metrics |

### Important table grains

**transaction_state**

One record per order.

Contains the current transaction information, including order ID, customer ID, product ID, quantity, unit price, business date, latest version, active status, and last event ID.

**event_history**

One record per accepted transaction event, preserving event history and version information.

**processed_events**

One record per successfully processed event ID, supporting duplicate detection and idempotency.

**rejected_records**

Stores invalid source records with their associated error information and pipeline run ID.

**pipeline_runs**

Tracks individual pipeline executions, including run ID, source file, status, input count, accepted count, rejected count, and duplicate count.

**daily_product_metrics**

One record per business date and product ID.

| Column | Description |
|---|---|
| `business_date` | Business date of the transactions |
| `product_id` | Product identifier |
| `order_count` | Number of active orders |
| `total_quantity` | Total units sold |
| `total_revenue` | Total revenue |
| `updated_at` | Last metric recalculation timestamp |

### Key relationships and business rules

- Order IDs identify transactions.
- Event IDs uniquely identify source events.
- Customer IDs and product IDs reference their respective master tables.
- Transaction versions are checked before applying updates or reversals.
- Reversed transactions are excluded from daily product metrics.
- Historical accepted events are retained in `event_history`.
- Rejected records remain traceable to their pipeline run.

## 6. Event Processing Rules

The pipeline supports three event types.

| Event type | Behaviour |
|---|---|
| CREATE | Creates a new transaction with the initial version |
| UPDATE | Updates an existing active transaction |
| REVERSE | Marks an existing transaction inactive |

### Version handling

- CREATE requires the initial transaction version.
- UPDATE and REVERSE require the next expected version.
- Duplicate event IDs are skipped.
- Invalid or unexpected versions are rejected.

### Metric recomputation

When an order is created, updated, reversed, or arrives late, the pipeline identifies the affected business date and product.

It recalculates the relevant daily product metric groups instead of rebuilding the entire metrics table after every event.

## 7. Project Setup

### Prerequisites

Install:

- Java Development Kit 17.
- Apache Maven.
- Git.

Verify the installations:

```cmd
java -version
mvn -version
git --version
```

### Clone the repository

```cmd
git clone https://github.com/simranali1229/java-sqlite-data-pipeline.git
```

Navigate to the project:

```cmd
cd java-sqlite-data-pipeline
```

### Compile the project

```cmd
mvn clean compile
```

## 8. Run Automated Tests

Execute:

```cmd
mvn clean test
```

### Latest verified test results

| Test result | Count |
|---|---:|
| Total tests | 32 |
| Passed | 32 |
| Failed | 0 |
| Errors | 0 |
| Skipped | 0 |

**Latest build status: BUILD SUCCESS**

The tests cover event processing, duplicate detection, validation, transaction updates, reversals, metric recomputation, quoted CSV fields, late-arriving events, and manual replay of out-of-order events.

## 9. Generate Reproducible Transaction Events

The project includes `TransactionDataGenerator` for generating scalable synthetic transaction data.

The generator uses a fixed random seed for reproducibility.

### Build the Java dependency classpath

Run these commands in Windows CMD:

```cmd
mvn dependency:build-classpath -Dmdep.outputFile=classpath.txt
set /p CP=<classpath.txt
```

### Generate 100,000 transaction events

```cmd
java -cp "target\classes;%CP%" com.example.TransactionDataGenerator large_transaction_events.csv 100000
```

The generator produces synthetic CREATE events with reproducible values.

## 10. Run the Pipeline

Ensure that the input CSV file exists in the current project directory.

For example:

```cmd
java -cp "target\classes;%CP%" com.example.App transaction_events.csv
```

If no input filename is supplied, the application uses its default input filename:

```text
transaction_events.csv
```

The application processes the events, calculates overall metrics, displays top-selling products, and generates:

```text
daily_metrics_report.csv
```

**Important:** The default database is `product_metrics.db`. Use a separate database for test and benchmark runs to protect existing data.

## 11. Database Verification and Reconciliation

The project includes verification and reconciliation utilities.

### Database verification

```cmd
java -cp "target\classes;%CP%" com.example.DatabaseVerification
```

### Reconciliation report

```cmd
java -cp "target\classes;%CP%" com.example.ReconciliationReport
```

For verification against a specific database, use the database URL override:

```cmd
java -Ddb.url=jdbc:sqlite:test_metrics_event_benchmark.db -cp "target\classes;%CP%" com.example.ReconciliationReport
```

### Latest verified benchmark database results

| Reconciliation check | Result |
|---|---:|
| Event IDs missing from `processed_events` | 0 |
| Event IDs missing from `event_history` | 0 |
| Daily metric mismatches | 0 |
| Event history records | 270,011 |
| Processed event records | 270,011 |
| Inconsistent pipeline runs | 0 |

**Overall reconciliation: PASSED**

These results were obtained from the tested benchmark database.

## 12. Performance Benchmarking

The project includes `TransactionDataGenerator` and `PerformanceTestRunner` for generating and benchmarking transaction events.

### Latest 10,000-event benchmark

| Run | Processing time | Throughput |
|---|---:|---:|
| First run | 121.52 seconds | 82.29 events/sec |
| Second run | 118.17 seconds | 84.63 events/sec |
| Average | 119.85 seconds | 83.46 events/sec |

These are measured results from the local test environment.

Performance may vary depending on the hardware, database state, and environment.

### Optimization implemented

- Reuse of JDBC `PreparedStatement` objects.
- Database indexes for frequently accessed data.
- Incremental recomputation of affected metric groups.
- Avoiding unnecessary full-table metric recalculation during individual event processing.

## 13. Idempotency and Failure Handling

### Idempotency

Previously processed event IDs are detected through `processed_events`.

When the same event is submitted again, it is skipped rather than applied a second time.

### Failure handling

The pipeline uses database transactions and per-record savepoints.

Invalid records can be rolled back individually and recorded in `rejected_records`, while valid records can continue processing.

Pipeline execution details are recorded in `pipeline_runs`.

### Retry and recovery behaviour

- Reprocessing an already accepted event is safe because duplicate event IDs are detected.
- Rejected events can be corrected and submitted again.
- Out-of-order events are rejected when the required preceding version is missing.
- The missing event must arrive and the rejected event must be manually replayed.
- Automatic pending-event retry and backfill scheduling are not currently implemented.

## 14. Known Limitations

The following improvements are outside the completed implementation:

1. Automatic retry and queueing of out-of-order events.
2. Automatic restart and backfill orchestration.
3. Multiline quoted CSV field support.
4. Versioned database schema migrations.
5. Structured logging and advanced monitoring.
6. Further optimization for very large event volumes.

These limitations are documented to distinguish implemented functionality from future enhancements.

## 15. Design Decisions and Trade-offs

- **SQLite:** Selected for simple local execution without external database infrastructure.
- **Java 17:** Used for maintainable application development and modern language features.
- **CSV ingestion:** Provides a straightforward input mechanism for reproducible testing.
- **Incremental recomputation:** Limits aggregation work to affected date/product groups.
- **Event history:** Preserves accepted transaction changes for traceability.
- **Database transactions and savepoints:** Help isolate invalid records while maintaining transaction safety.
- **PreparedStatement reuse:** Reduces repeated SQL statement preparation.
- **Manual replay for out-of-order events:** Chosen as a simpler implementation within the challenge timeframe instead of building a pending-event queue.

## 16. Future Improvements

- Automatic out-of-order event retry.
- Improved restart and backfill recovery.
- Multiline CSV parsing.
- Versioned schema migrations.
- Structured logging and monitoring.
- Additional performance optimization.
- More extensive failure-injection and recovery tests.

## 17. Final Project Summary

This project demonstrates a working incremental data engineering pipeline using Java 17, SQLite, JDBC, Maven, and JUnit.

The implementation includes:

- CSV transaction event ingestion.
- CREATE, UPDATE, and REVERSE processing.
- Duplicate detection and idempotency.
- Transaction state and event history.
- Rejected-record tracking.
- Pipeline run tracking.
- Daily product metrics.
- Late-event metric recomputation.
- Automated testing.
- Data reconciliation.
- Performance benchmarking.

**Latest verified automated tests: 32 passed, 0 failed.**

**Latest benchmark reconciliation: PASSED.**

The project prioritizes correctness, reproducibility, traceability, and maintainable implementation within the 72-hour challenge scope.
