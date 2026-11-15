package com.example;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Random;

public class TransactionDataGenerator {

    private static final int[] CUSTOMER_IDS = {1, 2, 3, 4, 5};
    private static final int[] PRODUCT_IDS = {1, 2, 3, 4, 5};

    private static final double[] PRODUCT_PRICES = {
        55000, 20000, 1500, 1200, 12000
    };

    public static void main(String[] args) {

        String fileName = args.length > 0
                ? args[0] : "large_transaction_events.csv";

        int recordCount = args.length > 1
                ? Integer.parseInt(args[1]) : 100000;

        String eventIdPrefix = args.length > 2
                ? args[2] : "LOAD";

        int orderIdBase = args.length > 3
                ? Integer.parseInt(args[3]) : 1000000;

        if (recordCount <= 0) {
            System.out.println("Record count must be greater than zero.");
            return;
        }

        if (eventIdPrefix == null || eventIdPrefix.trim().isEmpty()
                || eventIdPrefix.contains(",")) {
            System.out.println("Invalid event ID prefix.");
            return;
        }

        if (orderIdBase < 0
                || (long) orderIdBase + recordCount > Integer.MAX_VALUE) {
            System.out.println("Invalid order ID range.");
            return;
        }

        try {
            generateCsv(
                    fileName,
                    recordCount,
                    eventIdPrefix,
                    orderIdBase
            );

            System.out.println("--------------------------------");
            System.out.println("DATA GENERATION COMPLETED");
            System.out.println("File: " + fileName);
            System.out.println("Records generated: " + recordCount);
            System.out.println("Event ID prefix: " + eventIdPrefix);
            System.out.println("Order ID base: " + orderIdBase);
            System.out.println("--------------------------------");

        } catch (IOException e) {
            System.out.println("Error generating CSV: " + e.getMessage());
        }
    }

    // Keeps the original method available for existing callers.
    public static void generateCsv(String fileName, int recordCount)
            throws IOException {

        generateCsv(fileName, recordCount, "LOAD", 1000000);
    }

    public static void generateCsv(
            String fileName,
            int recordCount,
            String eventIdPrefix,
            int orderIdBase
    ) throws IOException {

        Random random = new Random(42);

        LocalDate startDate = LocalDate.of(2026, 11, 1);

        LocalDateTime startTimestamp =
                LocalDateTime.of(2026, 11, 14, 0, 0);

        try (BufferedWriter writer =
                     new BufferedWriter(new FileWriter(fileName))) {

            writer.write(
                "event_id,order_id,event_type,event_version,"
                + "customer_id,product_id,quantity,unit_price,"
                + "business_date,event_timestamp"
            );

            writer.newLine();

            for (int i = 1; i <= recordCount; i++) {

                String eventId = String.format(
                        "%s_%06d", eventIdPrefix, i
                );

                int orderId = orderIdBase + i;

                int customerId = CUSTOMER_IDS[
                        random.nextInt(CUSTOMER_IDS.length)];

                int productIndex =
                        random.nextInt(PRODUCT_IDS.length);

                int productId = PRODUCT_IDS[productIndex];

                int quantity = random.nextInt(5) + 1;

                double unitPrice = PRODUCT_PRICES[productIndex];

                LocalDate businessDate =
                        startDate.plusDays(random.nextInt(30));

                LocalDateTime eventTimestamp =
                        startTimestamp.plusSeconds(i);

                String row = String.join(",",
                        eventId,
                        String.valueOf(orderId),
                        "CREATE",
                        "1",
                        String.valueOf(customerId),
                        String.valueOf(productId),
                        String.valueOf(quantity),
                        String.valueOf(unitPrice),
                        businessDate.toString(),
                        eventTimestamp.toString()
                );

                writer.write(row);
                writer.newLine();
            }
        }
    }
}
