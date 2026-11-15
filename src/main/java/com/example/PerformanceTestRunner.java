package com.example;

public class PerformanceTestRunner {

    public static void main(String[] args) {

        if (args.length != 2) {
            System.out.println(
                "Usage: PerformanceTestRunner <database-file> <csv-file>"
            );
            return;
        }

        System.setProperty(
            "db.url",
            "jdbc:sqlite:" + args[0]
        );

        System.out.println("Performance database: " + args[0]);
        System.out.println("Processing file: " + args[1]);

        try {
            EventProcessor.processCsv(args[1]);
        } catch (Exception e) {
            System.err.println("Performance test failed!");
            e.printStackTrace();
        }
    }
}
