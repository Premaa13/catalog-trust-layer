package com.catalogtrust.repository;

import com.catalogtrust.model.CatalogSnapshot;

import java.io.IOException;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Loads catalog rows from the existing catalog.csv file into Java snapshots.
 */
public class CatalogCsvRepository {
    private static final String DATA_NAME = "Catalog";
    private static final String[] COLUMNS = {
            "product_id", "product_name", "category", "price", "stock", "last_updated", "date_added"
    };

    private final Path csvFile;

    public CatalogCsvRepository(Path csvFile) {
        this.csvFile = Objects.requireNonNull(csvFile, "csvFile must not be null");
    }

    public List<CatalogSnapshot> findAll() throws IOException {
        List<CatalogSnapshot> snapshots = new ArrayList<>();
        for (CsvFileReader.Row row : CsvFileReader.readRows(csvFile, DATA_NAME, COLUMNS)) {
            String productId = requiredValue(row, "product_id");
            String productName = requiredValue(row, "product_name");
            String category = requiredValue(row, "category");
            String dateAdded = requiredValue(row, "date_added");
            double price = parseNumber(row, "price");
            double stock = parseNumber(row, "stock");
            OffsetDateTime lastUpdated = parseTimestamp(row, "last_updated");

            snapshots.add(new CatalogSnapshot(
                    productId, productName, category, price, stock, lastUpdated, dateAdded
            ));
        }
        return List.copyOf(snapshots);
    }

    private String requiredValue(CsvFileReader.Row row, String column) throws IOException {
        String value = row.values().get(column);
        if (value == null || value.isBlank()) {
            throw invalidValue(row, column, "value must not be blank", null);
        }
        return value;
    }

    private double parseNumber(CsvFileReader.Row row, String column) throws IOException {
        String value = requiredValue(row, column);
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException exception) {
            throw invalidValue(row, column, "not a valid number: '" + value + "'", exception);
        }
    }

    private OffsetDateTime parseTimestamp(CsvFileReader.Row row, String column) throws IOException {
        String value = requiredValue(row, column);
        try {
            return OffsetDateTime.parse(value);
        } catch (DateTimeParseException exception) {
            throw invalidValue(row, column, "not a valid ISO-8601 timestamp: '" + value + "'", exception);
        }
    }

    private IOException invalidValue(
            CsvFileReader.Row row,
            String column,
            String detail,
            Exception cause
    ) {
        String message = "Invalid catalog CSV file '" + csvFile + "' at line " + row.lineNumber()
                + ", column '" + column + "': " + detail;
        return cause == null ? new IOException(message) : new IOException(message, cause);
    }
}
