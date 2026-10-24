package com.catalogtrust.mcp;

import com.catalogtrust.model.TrustScoringResult;
import com.catalogtrust.repository.AuditLogCsvRepository;
import com.catalogtrust.service.ProjectDataPaths;
import com.catalogtrust.service.TrustScoringService;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ListingTrustMcpToolTest {
    @Test
    void rejectsBlankProductIds() {
        ListingTrustMcpTool tool = newTool(mock(TrustScoringService.class));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> tool.verifyListingTrust(" ")
        );

        assertEquals("product_id is required and must not be blank", exception.getMessage());
    }

    @Test
    void returnsReviewAndBlockDecisionsWithoutChangingThem() throws IOException {
        TrustScoringService scorer = mock(TrustScoringService.class);
        ListingTrustMcpTool tool = newTool(scorer);
        when(scorer.evaluateProduct(anyString(), any(), any()))
                .thenReturn(new TrustScoringResult("TEST-A", "Product", 50, "FLAGGED", "REVIEW",
                        List.of("review reason")))
                .thenReturn(new TrustScoringResult("TEST-A", "Product", 10, "FLAGGED", "BLOCK",
                        List.of("block reason")));

        Map<String, Object> review = tool.verifyListingTrust("TEST-A");
        Map<String, Object> block = tool.verifyListingTrust("TEST-A");

        assertEquals("REVIEW", review.get("action"));
        assertEquals(List.of("review reason"), review.get("reasons"));
        assertEquals("BLOCK", block.get("action"));
        assertEquals(List.of("block reason"), block.get("reasons"));
    }

    private ListingTrustMcpTool newTool(TrustScoringService scorer) {
        ProjectDataPaths paths = mock(ProjectDataPaths.class);
        when(paths.dataFile("catalog.csv")).thenReturn(findProjectRoot().resolve("data/catalog.csv"));
        when(paths.dataFile("orders.csv")).thenReturn(findProjectRoot().resolve("data/orders.csv"));
        return new ListingTrustMcpTool(paths, scorer, mock(AuditLogCsvRepository.class));
    }

    private static java.nio.file.Path findProjectRoot() {
        java.nio.file.Path current = java.nio.file.Path.of("").toAbsolutePath();
        while (current != null) {
            if (java.nio.file.Files.isRegularFile(current.resolve("data/catalog.csv"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Could not locate the existing data/catalog.csv fixture");
    }
}
