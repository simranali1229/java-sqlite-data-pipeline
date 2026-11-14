
# Daily Product and Customer Metrics Data Engineering Pipeline

## 1. Project Overview

This project implements a Java-based data engineering pipeline using SQLite.

It processes transaction events from CSV files and maintains product and customer order data.

The pipeline supports incremental ingestion, duplicate detection, order updates, reversals, event history, and daily product metrics.

## 2. Technologies Used

- Java 17
- SQLite
- JDBC
- Maven
- JUnit 4
- CSV

## 3. Main Features

- Product and customer database management
- CSV transaction event ingestion
- CREATE, UPDATE, and REVERSE event processing
- Incremental data processing
- Duplicate event detection
- Rejected record tracking
- Pipeline execution history
- Daily product metrics
- 100,000-record test data generation
- Data reconciliation and performance reporting

## 4. Project Structure

```text
data-pipeline-project/
|
|-- src/
|   |-- main/
|   |   |-- java/com/example/
|   |       |-- App.java
|   |       |-- DatabaseConnection.java
|   |       |-- DatabaseSetup.java
|   |       |-- DataSeeder.java
|   |       |-- EventProcessor.java
|   |       |-- TransactionDataGenerator.java
|   |       |-- BulkIngestionRunner.java
|   |       |-- DatabaseVerification.java
|   |       |-- ReconciliationReport.java
|   |
|   |-- test/
|       |-- java/com/example/
|
|-- product_metrics.db
|-- transaction_events.csv
|-- large_transaction_events.csv
|-- pom.xml
|-- README.md
```

## 5. Database Tables

- products: Product information
- customers: Customer information
- orders: Original sample orders
- transaction_state: Latest order state
- event_history: Historical transaction events
- processed_events: Duplicate event tracking
- rejected_records: Invalid event records
- pipeline_runs: Pipeline execution details

## 6. Build the Project

```bash
mvn clean compile
```

## 7. Run Unit Tests

```bash
mvn clean test
```

## 8. Generate 100,000 Transaction Events

```bash
java -cp target/classes com.example.TransactionDataGenerator large_transaction_events.csv 100000
```

## 9. Run Bulk Ingestion

Generate the dependency classpath:

```bash
mvn dependency:build-classpath -Dmdep.outputFile=classpath.txt
```

Run the bulk ingestion:

```powershell
powershell -NoProfile -Command "$cp = (Get-Content .\classpath.txt -Raw).Trim(); java -cp ('target\classes;' + $cp) com.example.BulkIngestionRunner large_transaction_events.csv"
```

## 10. Verify Database Records

```powershell
powershell -NoProfile -Command "$cp = (Get-Content .\classpath.txt -Raw).Trim(); java -cp ('target\classes;' + $cp) com.example.DatabaseVerification"
```

## 11. Run Data Reconciliation

```powershell
powershell -NoProfile -Command "$cp = (Get-Content .\classpath.txt -Raw).Trim(); java -cp ('target\classes;' + $cp) com.example.ReconciliationReport"
```

## 12. Idempotency

The pipeline tracks processed event IDs.

When the same CSV file is processed again, previously processed events are detected as duplicates and are not inserted again.

## 13. Testing Results

The project has passed 17 automated tests in the current test suite.

A 100,000-record ingestion run accepted all 100,000 records with zero rejected records.

A second run detected all 100,000 records as duplicates.

## 14. Future Improvements

- Stronger data reconciliation
- Improved CSV parsing for quoted fields
- Schema drift handling
- Better failure recovery
- Performance optimization
- Detailed logging and monitoring
