package com.catalogtrust.service;

import com.catalogtrust.model.TrustScoringResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrustEngineServiceTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void matchesPythonReferenceForPlantedProductsAndWritesSortedResults() throws IOException {
        Path projectRoot = findProjectRoot();
        Path resultsFile = temporaryDirectory.resolve("results.csv");
        TrustEngineService service = new TrustEngineService(new TrustScoringService());

        List<TrustScoringResult> results = service.run(
                projectRoot.resolve("data/catalog.csv"),
                projectRoot.resolve("data/orders.csv"),
                resultsFile
        );

        assertEquals(70, results.size());
        assertEquals(1, results.stream().filter(result -> result.productId().equals("TEST-A")).count());
        assertEquals(24, results.stream()
                .filter(result -> result.status().equals("VERIFIED") && result.action().equals("ALLOW"))
                .count());
        assertEquals(3, results.stream().filter(result -> result.action().equals("ALLOW_WITH_WARNING")).count());
        assertEquals(43, results.stream().filter(result -> result.action().equals("REVIEW")).count());
        assertEquals(0, results.stream().filter(result -> result.action().equals("BLOCK")).count());

        assertResult(results, "TEST-A", 50, "FLAGGED", "REVIEW", List.of(
                "Price increased 70.0% in 2.0 hours (high-risk)"
        ));
        assertResult(results, "TEST-B", 80, "FLAGGED", "ALLOW_WITH_WARNING", List.of(
                "High sales velocity relative to claimed stock"
        ));
        assertResult(results, "TEST-C", 60, "FLAGGED", "REVIEW", List.of(
                "Listing stale: not updated in 120.0 hours (severe)",
                "SAFETY OVERRIDE: severe staleness forces REVIEW regardless of score"
        ));
        assertResult(results, "TEST-D", 100, "VERIFIED", "ALLOW", List.of());

        List<String> csvLines = Files.readAllLines(resultsFile);
        assertEquals(
                "product_id,product_name,trust_score,status,action,reasons",
                csvLines.get(0)
        );
        assertEquals(71, csvLines.size());
        assertTrue(csvLines.get(1).startsWith("PROD-0001,"));
        assertTrue(csvLines.get(csvLines.size() - 1).startsWith("TEST-D,"));
        assertTrue(csvLines.stream().anyMatch(line -> line.equals(
                "TEST-C,Cotton Casual Shirt,60,FLAGGED,REVIEW,"
                        + "Listing stale: not updated in 120.0 hours (severe); "
                        + "SAFETY OVERRIDE: severe staleness forces REVIEW regardless of score"
        )));
    }

    private static void assertResult(
            List<TrustScoringResult> results,
            String productId,
            int score,
            String status,
            String action,
            List<String> reasons
    ) {
        TrustScoringResult result = results.stream()
                .filter(candidate -> candidate.productId().equals(productId))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing result for " + productId));
        assertEquals(score, result.trustScore());
        assertEquals(status, result.status());
        assertEquals(action, result.action());
        assertEquals(reasons, result.reasons());
    }

    private static Path findProjectRoot() {
        Path currentDirectory = Path.of("").toAbsolutePath();
        while (currentDirectory != null) {
            if (Files.exists(currentDirectory.resolve("data/catalog.csv"))) {
                return currentDirectory;
            }
            currentDirectory = currentDirectory.getParent();
        }
        throw new IllegalStateException("Could not locate the existing data/catalog.csv fixture");
    }
}
