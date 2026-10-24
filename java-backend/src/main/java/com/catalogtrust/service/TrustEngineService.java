package com.catalogtrust.service;

import com.catalogtrust.model.CatalogSnapshot;
import com.catalogtrust.model.OrderRecord;
import com.catalogtrust.model.TrustScoringResult;
import com.catalogtrust.repository.CatalogCsvRepository;
import com.catalogtrust.repository.OrderCsvRepository;
import org.springframework.stereotype.Service;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Runs the scoring service once for each distinct catalog product and writes results.csv.
 */
@Service
public class TrustEngineService {
    private static final String RESULTS_HEADER =
            "product_id,product_name,trust_score,status,action,reasons";

    private final TrustScoringService scoringService;

    public TrustEngineService(TrustScoringService scoringService) {
        this.scoringService = scoringService;
    }

    public List<TrustScoringResult> run(Path catalogFile, Path ordersFile, Path resultsFile)
            throws IOException {
        List<CatalogSnapshot> catalog = new CatalogCsvRepository(catalogFile).findAll();
        List<OrderRecord> orders = new OrderCsvRepository(ordersFile).findAll();
        Map<String, List<CatalogSnapshot>> snapshotsByProduct = groupSnapshots(catalog);

        List<String> productIds = new ArrayList<>(snapshotsByProduct.keySet());
        productIds.sort(String::compareTo);

        List<TrustScoringResult> results = new ArrayList<>();
        for (String productId : productIds) {
            // Keep the full catalog so freshness uses the latest timestamp across all products.
            results.add(scoringService.evaluateProduct(productId, catalog, orders));
        }

        writeResults(resultsFile, results);
        return List.copyOf(results);
    }

    public void printSummary(List<TrustScoringResult> results, Path resultsFile) {
        List<TrustScoringResult> testProducts = results.stream()
                .filter(result -> result.productId().matches("TEST-[ABCD]"))
                .sorted((left, right) -> left.productId().compareTo(right.productId()))
                .toList();
        List<TrustScoringResult> normalProducts = results.stream()
                .filter(result -> !result.productId().matches("TEST-[ABCD]"))
                .toList();

        System.out.println("=".repeat(120));
        System.out.println("TRUST ENGINE RESULTS SUMMARY");
        System.out.println("=".repeat(120));
        System.out.println();
        System.out.println("PLANTED TEST CASES (HIGHLIGHTED):");
        System.out.println("-".repeat(120));
        printResultsTable(testProducts);
        System.out.println();
        System.out.println("NORMAL PRODUCTS:");
        System.out.println("-".repeat(120));
        printResultsTable(normalProducts);
        System.out.println();
        System.out.println("=".repeat(120));
        System.out.println("SUMMARY STATISTICS");
        System.out.println("-".repeat(120));
        System.out.println("Total products evaluated: " + results.size());
        System.out.println("VERIFIED (ALLOW): " + countStatusAndAction(results, "VERIFIED", "ALLOW"));
        System.out.println("FLAGGED - ALLOW_WITH_WARNING: " + countAction(results, "ALLOW_WITH_WARNING"));
        System.out.println("FLAGGED - REVIEW: " + countAction(results, "REVIEW"));
        System.out.println("FLAGGED - BLOCK: " + countAction(results, "BLOCK"));
        System.out.println("=".repeat(120));
        System.out.println();
        System.out.println("Results written to: " + resultsFile.toAbsolutePath());
    }

    private Map<String, List<CatalogSnapshot>> groupSnapshots(List<CatalogSnapshot> catalog) {
        Map<String, List<CatalogSnapshot>> snapshotsByProduct = new LinkedHashMap<>();
        for (CatalogSnapshot snapshot : catalog) {
            snapshotsByProduct.computeIfAbsent(snapshot.productId(), ignored -> new ArrayList<>())
                    .add(snapshot);
        }
        return snapshotsByProduct;
    }

    private void writeResults(Path resultsFile, List<TrustScoringResult> results) throws IOException {
        Path parent = resultsFile.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        try (BufferedWriter writer = Files.newBufferedWriter(resultsFile, StandardCharsets.UTF_8)) {
            writer.write(RESULTS_HEADER);
            writer.newLine();
            for (TrustScoringResult result : results) {
                String reasons = String.join("; ", result.reasons());
                writeCsvRow(writer, List.of(
                        result.productId(),
                        result.productName(),
                        Integer.toString(result.trustScore()),
                        result.status(),
                        result.action(),
                        reasons
                ));
            }
        } catch (IOException exception) {
            throw new IOException("Unable to write trust engine results to '" + resultsFile + "'", exception);
        }
    }

    private void writeCsvRow(BufferedWriter writer, List<String> values) throws IOException {
        for (int index = 0; index < values.size(); index++) {
            if (index > 0) {
                writer.write(',');
            }
            writer.write(escapeCsvField(values.get(index)));
        }
        writer.newLine();
    }

    private String escapeCsvField(String value) {
        if (value.contains(",") || value.contains("\"") || value.contains("\r") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private void printResultsTable(List<TrustScoringResult> results) {
        System.out.printf("%-12s %-12s %-12s %-20s%n", "product_id", "trust_score", "status", "action");
        System.out.println("-".repeat(120));
        for (TrustScoringResult result : results) {
            System.out.printf(
                    "%-12s %-12d %-12s %-20s%n",
                    result.productId(), result.trustScore(), result.status(), result.action()
            );
        }

        List<TrustScoringResult> withReasons = results.stream()
                .filter(result -> !result.reasons().isEmpty())
                .toList();
        if (!withReasons.isEmpty()) {
            System.out.println();
            System.out.println("REASONS FOR FLAGGED PRODUCTS:");
            System.out.println("-".repeat(120));
            for (TrustScoringResult result : withReasons) {
                System.out.println(result.productId() + ": " + String.join("; ", result.reasons()));
            }
        }
    }

    private long countStatusAndAction(List<TrustScoringResult> results, String status, String action) {
        return results.stream()
                .filter(result -> status.equals(result.status()) && action.equals(result.action()))
                .count();
    }

    private long countAction(List<TrustScoringResult> results, String action) {
        return results.stream().filter(result -> action.equals(result.action())).count();
    }
}
