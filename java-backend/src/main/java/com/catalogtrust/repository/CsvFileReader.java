package com.catalogtrust.repository;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reads simple, line-based CSV files and checks their headers and row widths.
 */
final class CsvFileReader {
    private CsvFileReader() {
    }

    static List<Row> readRows(Path file, String dataName, String... requiredColumns) throws IOException {
        if (!Files.isRegularFile(file)) {
            throw new IOException(dataName + " CSV file not found or is not a regular file: " + file);
        }

        List<String> lines;
        try {
            lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IOException("Unable to read " + dataName + " CSV file: " + file, exception);
        }

        int headerLineIndex = 0;
        while (headerLineIndex < lines.size() && lines.get(headerLineIndex).isBlank()) {
            headerLineIndex++;
        }
        if (headerLineIndex == lines.size()) {
            throw new IOException(dataName + " CSV file is empty: " + file);
        }

        List<String> headers = parseLine(lines.get(headerLineIndex), file, headerLineIndex + 1);
        if (!headers.isEmpty() && headers.get(0).startsWith("\uFEFF")) {
            headers.set(0, headers.get(0).substring(1));
        }
        Set<String> uniqueHeaders = new HashSet<>();
        for (String header : headers) {
            if (header.isBlank()) {
                throw malformed(file, headerLineIndex + 1, "header contains a blank column name");
            }
            if (!uniqueHeaders.add(header)) {
                throw malformed(file, headerLineIndex + 1, "duplicate column name '" + header + "'");
            }
        }
        for (String column : requiredColumns) {
            if (!uniqueHeaders.contains(column)) {
                throw malformed(file, headerLineIndex + 1, "missing required column '" + column + "'");
            }
        }

        List<Row> rows = new ArrayList<>();
        for (int lineIndex = headerLineIndex + 1; lineIndex < lines.size(); lineIndex++) {
            String line = lines.get(lineIndex);
            if (line.isBlank()) {
                continue;
            }
            List<String> fields = parseLine(line, file, lineIndex + 1);
            if (fields.size() != headers.size()) {
                throw malformed(file, lineIndex + 1,
                        "expected " + headers.size() + " columns but found " + fields.size());
            }

            Map<String, String> values = new LinkedHashMap<>();
            for (int columnIndex = 0; columnIndex < headers.size(); columnIndex++) {
                values.put(headers.get(columnIndex), fields.get(columnIndex));
            }
            rows.add(new Row(lineIndex + 1, values));
        }

        if (rows.isEmpty()) {
            throw new IOException(dataName + " CSV file contains a header but no data rows: " + file);
        }
        return rows;
    }

    private static List<String> parseLine(String line, Path file, int lineNumber) throws IOException {
        List<String> fields = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean insideQuotes = false;
        boolean quoteClosed = false;

        for (int index = 0; index < line.length(); index++) {
            char character = line.charAt(index);
            if (insideQuotes) {
                if (character == '"') {
                    if (index + 1 < line.length() && line.charAt(index + 1) == '"') {
                        field.append('"');
                        index++;
                    } else {
                        insideQuotes = false;
                        quoteClosed = true;
                    }
                } else {
                    field.append(character);
                }
            } else if (character == ',') {
                fields.add(field.toString());
                field.setLength(0);
                quoteClosed = false;
            } else if (character == '"' && field.length() == 0 && !quoteClosed) {
                insideQuotes = true;
            } else if (quoteClosed) {
                throw malformed(file, lineNumber, "unexpected character after a quoted field");
            } else if (character == '"') {
                throw malformed(file, lineNumber, "quote must begin a CSV field");
            } else {
                field.append(character);
            }
        }

        if (insideQuotes) {
            throw malformed(file, lineNumber, "quoted field is not closed");
        }
        fields.add(field.toString());
        return fields;
    }

    private static IOException malformed(Path file, int lineNumber, String detail) {
        return new IOException("Malformed CSV file '" + file + "' at line " + lineNumber + ": " + detail);
    }

    record Row(int lineNumber, Map<String, String> values) {
    }
}
