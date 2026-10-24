package com.catalogtrust.agent;

import com.catalogtrust.payment.RazorpayOrderResponse;
import com.catalogtrust.repository.AuditLogCsvRepository;
import com.catalogtrust.service.ProjectDataPaths;
import com.catalogtrust.service.PurchaseService;
import com.catalogtrust.service.TrustScoringService;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

class ShoppingAgentServiceTest {
    @Test
    void returnsSafeRecommendationsAndTrustExplanations() throws IOException {
        ShoppingAgentService service = createService();

        List<Map<String, Object>> events = service.runQuery("show me a good LED Table Lamp under 5000");

        assertEquals("understanding", events.get(0).get("step"));
        assertEquals("parsed", events.get(1).get("step"));
        assertEquals("led table lamp", events.get(1).get("keyword"));
        Map<String, Object> completed = event(events, "search_complete");
        List<?> recommendations = (List<?>) completed.get("recommendations");
        assertFalse(recommendations.isEmpty());
        assertTrue(recommendations.stream().allMatch(recommendation -> {
            Map<?, ?> product = (Map<?, ?>) recommendation;
            return List.of("ALLOW", "ALLOW_WITH_WARNING").contains(product.get("action"));
        }));
    }

    @Test
    void returnsNoResultsForUnknownProducts() throws IOException {
        ShoppingAgentService service = createService();

        List<Map<String, Object>> events = service.runQuery("find a quantum flux capacitor");

        assertEquals("no_results", events.get(events.size() - 1).get("step"));
        assertEquals(0, event(events, "search_results").get("count"));
    }

    @Test
    void reportsAmbiguousInputUsingTheSameFallbackKeywordAsPython() throws IOException {
        ShoppingAgentService service = createService();

        List<Map<String, Object>> events = service.runQuery("cheap under 100");

        assertEquals("product", events.get(1).get("keyword"));
        assertEquals(new java.math.BigDecimal("100"), events.get(1).get("max_price"));
    }

    @Test
    void excludesBlockedListingsAndDoesNotAttemptPaymentDuringRecommendations() throws IOException {
        PurchaseService purchaseService = mock(PurchaseService.class);
        ShoppingAgentService service = createService(purchaseService);

        List<Map<String, Object>> events = service.runQuery("TEST-C");

        Map<String, Object> blockedTrustResult = events.stream()
                .filter(event -> "trust_result".equals(event.get("step")))
                .filter(event -> "TEST-C".equals(event.get("product_id")))
                .findFirst()
                .orElseThrow();
        assertEquals("REVIEW", blockedTrustResult.get("action"));
        assertTrue(events.stream().anyMatch(event -> "skipping".equals(event.get("step"))));
        assertFalse(recommendations(events).stream().anyMatch(
                recommendation -> "TEST-C".equals(((Map<?, ?>) recommendation).get("product_id"))
        ));
        verify(purchaseService, never()).buy(any());
    }

    @Test
    void agentShopDelegatesAnyOrderCreationToTrustGatedPurchaseService() throws IOException {
        PurchaseService purchaseService = mock(PurchaseService.class);
        when(purchaseService.buy("TEST-D")).thenReturn(Map.of(
                "product_id", "TEST-D",
                "product_name", "LED Table Lamp",
                "status", "VERIFIED",
                "action", "ALLOW",
                "trust_score", 100,
                "reasons", List.of(),
                "purchase_status", "ORDER_CREATED",
                "order_id", "order_test"
        ));
        ShoppingAgentService service = createService(purchaseService);

        Map<String, Object> result = service.runShopGoal("TEST-D");

        Map<?, ?> finalResult = (Map<?, ?>) result.get("final_result");
        assertEquals("ORDER_CREATED", finalResult.get("purchase_status"));
        verify(purchaseService).buy(eq("TEST-D"));
    }

    private ShoppingAgentService createService() throws IOException {
        return createService(mock(PurchaseService.class));
    }

    private ShoppingAgentService createService(PurchaseService purchaseService) throws IOException {
        Path projectRoot = findProjectRoot();
        ProjectDataPaths paths = mock(ProjectDataPaths.class);
        when(paths.dataFile("catalog.csv")).thenReturn(projectRoot.resolve("data/catalog.csv"));
        when(paths.dataFile("orders.csv")).thenReturn(projectRoot.resolve("data/orders.csv"));
        return new ShoppingAgentService(
                new ShoppingIntentParser(),
                paths,
                new TrustScoringService(),
                purchaseService
        );
    }

    private static Map<String, Object> event(List<Map<String, Object>> events, String step) {
        return events.stream()
                .filter(event -> step.equals(event.get("step")))
                .findFirst()
                .orElseThrow();
    }

    private static List<?> recommendations(List<Map<String, Object>> events) {
        return (List<?>) event(events, "search_complete").getOrDefault("recommendations", List.of());
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
