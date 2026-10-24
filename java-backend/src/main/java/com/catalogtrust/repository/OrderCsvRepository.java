package com.catalogtrust.repository;

import com.catalogtrust.model.OrderRecord;

import java.io.IOException;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Loads order rows from the existing orders.csv file into Java order records.
 */
public class OrderCsvRepository {
    private static final String DATA_NAME = "Order";
    private static final String[] COLUMNS = {"order_id", "product_id", "quantity", "timestamp", "status"};

    private final Path csvFile;

    public OrderCsvRepository(Path csvFile) {
        this.csvFile = Objects.requireNonNull(csvFile, "csvFile must not be null");
    }

    public List<OrderRecord> findAll() throws IOException {
        List<OrderRecord> orders = new ArrayList<>();
        for (CsvFileReader.Row row : CsvFileReader.readRows(csvFile, DATA_NAME, COLUMNS)) {
            String orderId = requiredValue(row, "order_id");
            String productId = requiredValue(row, "product_id");
            String status = requiredValue(row, "status");
            double quantity = parseQuantity(row);
            OffsetDateTime timestamp = parseTimestamp(row);

            orders.add(new OrderRecord(orderId, productId, quantity, timestamp, status));
        }
        return List.copyOf(orders);
    }

    private String requiredValue(CsvFileReader.Row row, String column) throws IOException {
        String value = row.values().get(column);
        if (value == null || value.isBlank()) {
            throw invalidValue(row, column, "value must not be blank", null);
        }
        return value;
    }

    private double parseQuantity(CsvFileReader.Row row) throws IOException {
        String value = requiredValue(row, "quantity");
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException exception) {
            throw invalidValue(row, "quantity", "not a valid number: '" + value + "'", exception);
        }
    }

    private OffsetDateTime parseTimestamp(CsvFileReader.Row row) throws IOException {
        String value = requiredValue(row, "timestamp");
        try {
            return OffsetDateTime.parse(value);
        } catch (DateTimeParseException exception) {
            throw invalidValue(row, "timestamp", "not a valid ISO-8601 timestamp: '" + value + "'", exception);
        }
    }

    private IOException invalidValue(
            CsvFileReader.Row row,
            String column,
            String detail,
            Exception cause
    ) {
        String message = "Invalid order CSV file '" + csvFile + "' at line " + row.lineNumber()
                + ", column '" + column + "': " + detail;
        return cause == null ? new IOException(message) : new IOException(message, cause);
    }
}
