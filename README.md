
# Daily Product and Customer Metrics Data Engineering Pipeline

## 1. Project Overview

A Java-based data engineering pipeline that processes transaction events from CSV files and maintains product, customer, and daily business metrics using SQLite.

The pipeline supports incremental event ingestion, transaction updates, reversals, duplicate detection, rejected-record tracking, and data reconciliation.

## 2. Technologies

- Java 17
- SQLite
- JDBC
- Maven
- JUnit 4
- CSV

## 3. Features

- Product and customer database management
- CSV transaction event ingestion
- CREATE, UPDATE, and REVERSE operations
- Incremental data processing
- Duplicate event detection
- Rejected record tracking
- Event history management
- Pipeline execution tracking
- Daily product metrics
- Transaction state management
- Data reconciliation
- Performance benchmarking
- PreparedStatement reuse

## 4. Database Tables

- products
- customers
- orders
- transaction_state
- event_history
- processed_events
- rejected_records
- pipeline_runs
- daily_product_metrics

## 5. Build the Project

```bash
mvn clean compile
```

## 6. Run Automated Tests

```bash
mvn clean test
```

Latest test result:

- Total tests: 32
- Passed: 32
- Failed: 0
- Errors: 0
- Skipped: 0

## 7. Generate Transaction Events

Generate 100,000 events:

```bash
mvn dependency:build-classpath -Dmdep.outputFile=classpath.txt
```

```bash
java -cp "target\classes;%CP%" com.example.TransactionDataGenerator large_transaction_events.csv 100000
```

## 8. Run Bulk Ingestion

```bash
java -cp "target\classes;%CP%" com.example.BulkIngestionRunner large_transaction_events.csv
```

## 9. Database Verification

```bash
java -cp "target\classes;%CP%" com.example.DatabaseVerification
```

## 10. Data Reconciliation

```bash
java -cp "target\classes;%CP%" com.example.ReconciliationReport
```

## 11. Performance Testing

The project includes PerformanceTestRunner and TransactionDataGenerator for benchmarking.

Latest 10,000-event benchmark:

| Run | Processing Time | Throughput |
|---|---:|---:|
| First | 121.52 seconds | 82.29 events/sec |
| Second | 118.17 seconds | 84.63 events/sec |
| Average | 119.85 seconds | 83.46 events/sec |

These are measured results from the local test environment.

## 12. Reconciliation Results

Latest benchmark database verification:

- Event IDs missing from processed_events: 0
- Event IDs missing from event_history: 0
- Daily metric mismatches: 0
- Event history records: 270011
- Processed event records: 270011
- Inconsistent pipeline runs: 0

Overall reconciliation: PASSED.

## 13. Idempotency

Previously processed event IDs are detected and skipped when the same events are submitted again.

## 14. Future Improvements

- More efficient bulk metric aggregation
- Improved multiline CSV parsing
- Schema evolution support
- Better failure recovery
- Performance monitoring
- Structured logging
