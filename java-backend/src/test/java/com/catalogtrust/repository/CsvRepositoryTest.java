package com.catalogtrust.repository;

import com.catalogtrust.model.CatalogSnapshot;
import com.catalogtrust.model.OrderRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CsvRepositoryTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void loadsTheExistingCatalogAndOrders() throws IOException {
        Path projectRoot = findProjectRoot();
        List<CatalogSnapshot> catalog =
                new CatalogCsvRepository(projectRoot.resolve("data/catalog.csv")).findAll();
        List<OrderRecord> orders =
                new OrderCsvRepository(projectRoot.resolve("data/orders.csv")).findAll();

        assertEquals(71, catalog.size());
        assertEquals(117, orders.size());

        List<CatalogSnapshot> testASnapshots = catalog.stream()
                .filter(snapshot -> snapshot.productId().equals("TEST-A"))
                .sorted((left, right) -> left.lastUpdated().compareTo(right.lastUpdated()))
                .toList();
        assertEquals(2, testASnapshots.size());
        assertEquals("Electronics", testASnapshots.get(0).category());
        assertEquals("Wireless Earbuds Pro", testASnapshots.get(0).productName());
        assertEquals(2, Duration.between(
                testASnapshots.get(0).lastUpdated(),
                testASnapshots.get(1).lastUpdated()
        ).toHours());
        assertTrue(testASnapshots.get(0).dateAdded().contains("+00:00"));

        assertEquals(3, orders.stream().filter(order -> order.productId().equals("TEST-B")).count());
        assertTrue(orders.stream().allMatch(order -> !order.orderId().isBlank()));
        assertTrue(orders.stream().allMatch(order -> order.status().equals("completed")));
    }

    @Test
    void readsQuotedFieldsAndPreservesTimestampOffset() throws IOException {
        Path catalogFile = temporaryDirectory.resolve("catalog.csv");
        Files.writeString(catalogFile, """
                product_id,product_name,category,price,stock,last_updated,date_added
                P-1,"Item, with comma",Home,12.50,4,2026-10-24T12:00:00+05:30,2026-10-01T12:00:00+05:30
                """);

        CatalogSnapshot snapshot = new CatalogCsvRepository(catalogFile).findAll().get(0);

        assertEquals("Item, with comma", snapshot.productName());
        assertEquals(12.50, snapshot.price());
        assertEquals(4.0, snapshot.stock());
        assertEquals("+05:30", snapshot.lastUpdated().getOffset().toString());
        assertEquals("2026-10-01T12:00:00+05:30", snapshot.dateAdded());
    }

    @Test
    void reportsMissingFilesClearly() {
        IOException catalogError = assertThrows(
                IOException.class,
                () -> new CatalogCsvRepository(temporaryDirectory.resolve("missing-catalog.csv")).findAll()
        );
        IOException orderError = assertThrows(
                IOException.class,
                () -> new OrderCsvRepository(temporaryDirectory.resolve("missing-orders.csv")).findAll()
        );

        assertTrue(catalogError.getMessage().contains("Catalog CSV file not found"));
        assertTrue(orderError.getMessage().contains("Order CSV file not found"));
    }

    @Test
    void reportsEmptyAndHeaderOnlyFilesClearly() throws IOException {
        Path emptyFile = temporaryDirectory.resolve("empty.csv");
        Files.writeString(emptyFile, "");
        IOException emptyError = assertThrows(
                IOException.class,
                () -> new CatalogCsvRepository(emptyFile).findAll()
        );

        Path headerOnlyFile = temporaryDirectory.resolve("header-only.csv");
        Files.writeString(headerOnlyFile,
                "order_id,product_id,quantity,timestamp,status\n");
        IOException headerOnlyError = assertThrows(
                IOException.class,
                () -> new OrderCsvRepository(headerOnlyFile).findAll()
        );

        assertTrue(emptyError.getMessage().contains("CSV file is empty"));
        assertTrue(headerOnlyError.getMessage().contains("no data rows"));
    }

    @Test
    void reportsMalformedRowsWithFileAndLine() throws IOException {
        Path catalogFile = temporaryDirectory.resolve("malformed-catalog.csv");
        Files.writeString(catalogFile, """
                product_id,product_name,category,price,stock,last_updated,date_added
                P-1,Item,Home,12.50,4,2026-10-24T12:00:00+05:30
                """);

        IOException error = assertThrows(
                IOException.class,
                () -> new CatalogCsvRepository(catalogFile).findAll()
        );

        assertTrue(error.getMessage().contains("malformed-catalog.csv"));
        assertTrue(error.getMessage().contains("line 2"));
        assertTrue(error.getMessage().contains("expected 7 columns but found 6"));
    }

    @Test
    void reportsInvalidTimestampsWithColumnAndLine() throws IOException {
        Path catalogFile = temporaryDirectory.resolve("invalid-time-catalog.csv");
        Files.writeString(catalogFile, """
                product_id,product_name,category,price,stock,last_updated,date_added
                P-1,Item,Home,12.50,4,not-a-timestamp,2026-10-01T12:00:00+05:30
                """);
        Path orderFile = temporaryDirectory.resolve("invalid-time-orders.csv");
        Files.writeString(orderFile, """
                order_id,product_id,quantity,timestamp,status
                O-1,P-1,2,not-a-timestamp,completed
                """);

        IOException catalogError = assertThrows(
                IOException.class,
                () -> new CatalogCsvRepository(catalogFile).findAll()
        );
        IOException orderError = assertThrows(
                IOException.class,
                () -> new OrderCsvRepository(orderFile).findAll()
        );

        assertTrue(catalogError.getMessage().contains("column 'last_updated'"));
        assertTrue(catalogError.getMessage().contains("line 2"));
        assertTrue(orderError.getMessage().contains("column 'timestamp'"));
        assertFalse(orderError.getMessage().isBlank());
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
