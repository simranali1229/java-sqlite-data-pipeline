package com.example;

public class BulkIngestionRunner {

    public static void main(String[] args) {

        String fileName = args.length > 0
                ? args[0] : "large_transaction_events.csv";

        try {
            // Prepare database and reference data
            DatabaseSetup.createTables();
            DataSeeder.insertSampleData();
            DatabaseSetup.createTables();

            System.out.println("\n========== BULK DATA INGESTION ==========");
            System.out.println("Input file: " + fileName);
            System.out.println("Starting ingestion...");

            long startTime = System.nanoTime();

            EventProcessor.processCsv(fileName);

            long endTime = System.nanoTime();

            double elapsedSeconds =
                    (endTime - startTime) / 1_000_000_000.0;

            System.out.println("\n========== PERFORMANCE REPORT ==========");
            System.out.printf("Execution time: %.2f seconds%n",
                    elapsedSeconds);

            System.out.println("========================================");

        } catch (Exception e) {
            System.out.println("Bulk ingestion failed: "
                    + e.getMessage());
            e.printStackTrace();
        }
    }
}
