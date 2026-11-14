# Java + SQLite Data Engineering Pipeline

## Project Overview
This project is a simple data engineering pipeline developed using Java and SQLite. It processes product, customer, and order data to calculate business metrics and generate a daily CSV report.

## Technologies Used
- Java 17
- SQLite
- JDBC
- Maven
- SQL
- CSV
- Windows Batch Script
- Windows Task Scheduler

## Project Features
- Creates and manages SQLite database tables.
- Loads sample product, customer, and order data.
- Connects Java to SQLite using JDBC.
- Calculates total revenue and total orders.
- Identifies top-selling products.
- Generates daily order, revenue, and units-sold metrics.
- Exports daily metrics to `daily_metrics_report.csv`.
- Logs pipeline execution status in `pipeline.log`.
- Supports execution through a Windows batch file and Task Scheduler.

## Database Tables
The project uses three main tables:

1. **Products** – stores product information.
2. **Customers** – stores customer information.
3. **Orders** – stores order details, including quantity, unit price, and order date.

## Project Structure

```text
data-pipeline-project/
│
├── src/
│   └── main/
│       └── java/
│           └── com/
│               └── example/
│                   ├── App.java
│                   ├── DatabaseSetup.java
│                   └── DataSeeder.java
│
├── product_metrics.db
├── pom.xml
├── run_pipeline.bat
├── daily_metrics_report.csv
├── pipeline.log
└── README.md
```

## How to Run the Project

### 1. Requirements
Install:
- JDK 17
- Apache Maven
- Visual Studio Code (or another Java IDE)

### 2. Clone the repository

```bash
git clone YOUR_GITHUB_REPOSITORY_URL
```

### 3. Open the project folder

```bash
cd data-pipeline-project
```

### 4. Run the pipeline

```bash
mvn clean compile exec:java
```

## Sample Output

| Metric | Result |
|---|---:|
| Total Revenue | 190400.00 |
| Total Orders | 8 |
| Headphones Sold | 4 |
| Mobile Sold | 3 |
| Laptop Sold | 2 |
| Keyboard Sold | 2 |
| Monitor Sold | 1 |

## Daily Metrics

| Date | Orders | Revenue | Units Sold |
|---|---:|---:|---:|
| 2026-11-10 | 2 | 95000.00 | 3 |
| 2026-11-11 | 2 | 3900.00 | 3 |
| 2026-11-12 | 2 | 32000.00 | 2 |
| 2026-11-13 | 2 | 59500.00 | 4 |

## Output Files
- `daily_metrics_report.csv` – generated daily metrics report.
- `pipeline.log` – records pipeline execution and success or failure.

## Automation
The `run_pipeline.bat` file runs the Java pipeline from Windows. It can be configured in Windows Task Scheduler for scheduled execution.

## Learning Outcomes
Through this project, I practiced:
- Java programming and JDBC connectivity.
- SQL queries, joins, grouping, and aggregation.
- Relational database concepts.
- Data transformation and reporting.
- CSV file generation.
- Maven project management.
- Basic pipeline automation and logging.

## Author
S Imran Ali