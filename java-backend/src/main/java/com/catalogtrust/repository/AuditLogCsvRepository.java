package com.catalogtrust.repository;

import org.springframework.stereotype.Repository;

import java.io.IOException;
import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads audit entries, fills empty reasons, and returns newest entries first.
 */
@Repository
public class AuditLogCsvRepository {
    private static final String[] COLUMNS = {
            "timestamp", "product_id", "product_name", "trust_score", "status", "action", "reasons"
    };

    public List<Map<String, Object>> findAllNewestFirst(Path csvFile) throws IOException {
        if (!Files.exists(csvFile)) {
            return List.of();
        }

        List<AuditEntry> entries = new ArrayList<>();
        for (CsvFileReader.Row row : CsvFileReader.readRows(csvFile, "Audit log", COLUMNS)) {
            String timestamp = requiredValue(row, "timestamp", csvFile);
            OffsetDateTime parsedTimestamp;
            try {
                parsedTimestamp = OffsetDateTime.parse(timestamp);
            } catch (DateTimeParseException exception) {
                throw invalidValue(row, csvFile, "timestamp",
                        "not a valid ISO-8601 timestamp: '" + timestamp + "'", exception);
            }

            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("timestamp", timestamp);
            entry.put("product_id", requiredValue(row, "product_id", csvFile));
            entry.put("product_name", requiredValue(row, "product_name", csvFile));
            entry.put("trust_score", parseScore(row, csvFile));
            entry.put("status", requiredValue(row, "status", csvFile));
            entry.put("action", requiredValue(row, "action", csvFile));
            entry.put("reasons", row.values().getOrDefault("reasons", ""));
            entries.add(new AuditEntry(parsedTimestamp, entry));
        }

        return entries.stream()
                .sorted(Comparator.comparing(AuditEntry::timestamp).reversed())
                .map(AuditEntry::values)
                .toList();
    }

    public void appendVerification(
            Path csvFile,
            String productId,
            String productName,
            int trustScore,
            String status,
            String action,
            String reasons
    ) throws IOException {
        Path parent = csvFile.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        boolean writeHeader = !Files.exists(csvFile) || Files.size(csvFile) == 0;
        try (BufferedWriter writer = Files.newBufferedWriter(
                csvFile,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND
        )) {
            if (writeHeader) {
                writer.write(String.join(",", COLUMNS));
                writer.newLine();
            }
            List<String> values = List.of(
                    OffsetDateTime.now(java.time.ZoneOffset.UTC).toString(),
                    productId,
                    productName,
                    Integer.toString(trustScore),
                    status,
                    action,
                    reasons
            );
            for (int index = 0; index < values.size(); index++) {
                if (index > 0) {
                    writer.write(',');
                }
                writer.write(escapeCsv(values.get(index)));
            }
            writer.newLine();
        } catch (IOException exception) {
            throw new IOException("Unable to append verification to audit log '" + csvFile + "'", exception);
        }
    }

    private String requiredValue(CsvFileReader.Row row, String column, Path file) throws IOException {
        String value = row.values().get(column);
        if (value == null || value.isBlank()) {
            throw invalidValue(row, file, column, "value must not be blank", null);
        }
        return value;
    }

    private Object parseScore(CsvFileReader.Row row, Path file) throws IOException {
        String score = requiredValue(row, "trust_score", file);
        try {
            return Integer.parseInt(score);
        } catch (NumberFormatException integerException) {
            try {
                return Double.parseDouble(score);
            } catch (NumberFormatException decimalException) {
                throw invalidValue(row, file, "trust_score", "not a valid number: '" + score + "'", decimalException);
            }
        }
    }

    private IOException invalidValue(
            CsvFileReader.Row row,
            Path file,
            String column,
            String detail,
            Exception cause
    ) {
        String message = "Invalid audit log CSV file '" + file + "' at line " + row.lineNumber()
                + ", column '" + column + "': " + detail;
        return cause == null ? new IOException(message) : new IOException(message, cause);
    }

    private String escapeCsv(String value) {
        if (value.contains(",") || value.contains("\"") || value.contains("\r") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private record AuditEntry(OffsetDateTime timestamp, Map<String, Object> values) {
    }
}
