package com.example;

public class MetricsTestRunner {

    public static void main(String[] args) {

        if (args.length != 1) {
            System.out.println(
                "Usage: MetricsTestRunner <csv-file>"
            );
            return;
        }

        try {
            // Use the separate test database.
            System.setProperty(
                "db.url",
                "jdbc:sqlite:test_metrics.db"
            );

            System.out.println(
                "Test database: test_metrics.db"
            );

            System.out.println(
                "Processing: " + args[0]
            );

            EventProcessor.processCsv(args[0]);

            System.out.println(
                "Test processing completed!"
            );

        } catch (Exception e) {
            System.err.println(
                "Test processing failed!"
            );
            e.printStackTrace();
        }
    }
}
