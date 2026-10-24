package com.catalogtrust.service;

import com.catalogtrust.model.CatalogSnapshot;
import com.catalogtrust.model.OrderRecord;
import com.catalogtrust.model.TrustScoringResult;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TrustScoringServiceTest {
    private static final TrustScoringService SERVICE = new TrustScoringService();
    private static List<CatalogSnapshot> catalog;
    private static List<OrderRecord> orders;

    @BeforeAll
    static void loadExistingSyntheticData() throws IOException {
        Path projectRoot = findProjectRoot();
        catalog = readCatalog(projectRoot.resolve("data/catalog.csv"));
        orders = readOrders(projectRoot.resolve("data/orders.csv"));
    }

    @Test
    void testA_matchesPriceSpikeFixture() {
        TrustScoringResult result = SERVICE.evaluateProduct("TEST-A", catalog, orders);

        assertEquals(50, result.trustScore());
        assertEquals("FLAGGED", result.status());
        assertEquals("REVIEW", result.action());
        assertEquals(List.of("Price increased 70.0% in 2.0 hours (high-risk)"), result.reasons());
    }

    @Test
    void testB_matchesStockMismatchFixture() {
        TrustScoringResult result = SERVICE.evaluateProduct("TEST-B", catalog, orders);

        assertEquals(80, result.trustScore());
        assertEquals("FLAGGED", result.status());
        assertEquals("ALLOW_WITH_WARNING", result.action());
        assertEquals(List.of("High sales velocity relative to claimed stock"), result.reasons());
    }

    @Test
    void testC_matchesStaleListingFixture() {
        TrustScoringResult result = SERVICE.evaluateProduct("TEST-C", catalog, orders);

        assertEquals(60, result.trustScore());
        assertEquals("FLAGGED", result.status());
        assertEquals("REVIEW", result.action());
        assertEquals(List.of(
                "Listing stale: not updated in 120.0 hours (severe)",
                "SAFETY OVERRIDE: severe staleness forces REVIEW regardless of score"
        ), result.reasons());
    }

    @Test
    void testD_matchesNormalControlFixture() {
        TrustScoringResult result = SERVICE.evaluateProduct("TEST-D", catalog, orders);

        assertEquals(100, result.trustScore());
        assertEquals("VERIFIED", result.status());
        assertEquals("ALLOW", result.action());
        assertEquals(List.of(), result.reasons());
    }

    @Test
    void missingProductUsesPythonErrorMessage() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> SERVICE.evaluateProduct("MISSING", catalog, orders)
        );

        assertEquals("product_id not found", exception.getMessage());
    }

    private static Path findProjectRoot() {
        Path currentDirectory = Path.of("").toAbsolutePath();
        if (Files.exists(currentDirectory.resolve("data/catalog.csv"))) {
            return currentDirectory;
        }
        Path parentDirectory = currentDirectory.getParent();
        if (parentDirectory != null && Files.exists(parentDirectory.resolve("data/catalog.csv"))) {
            return parentDirectory;
        }
        throw new IllegalStateException("Could not locate the existing data/catalog.csv fixture");
    }

    private static List<CatalogSnapshot> readCatalog(Path file) throws IOException {
        List<String> lines = Files.readAllLines(file);
        List<CatalogSnapshot> rows = new ArrayList<>();
        for (int index = 1; index < lines.size(); index++) {
            String[] columns = lines.get(index).split(",", -1);
            rows.add(new CatalogSnapshot(
                    columns[0],
                    columns[1],
                    Double.parseDouble(columns[3]),
                    Double.parseDouble(columns[4]),
                    OffsetDateTime.parse(columns[5])
            ));
        }
        return rows;
    }

    private static List<OrderRecord> readOrders(Path file) throws IOException {
        List<String> lines = Files.readAllLines(file);
        List<OrderRecord> rows = new ArrayList<>();
        for (int index = 1; index < lines.size(); index++) {
            String[] columns = lines.get(index).split(",", -1);
            rows.add(new OrderRecord(
                    columns[1],
                    Double.parseDouble(columns[2]),
                    OffsetDateTime.parse(columns[3]),
                    columns[4]
            ));
        }
        return rows;
    }
}
