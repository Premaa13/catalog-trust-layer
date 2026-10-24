package com.catalogtrust.mcp;

import com.catalogtrust.model.CatalogSnapshot;
import com.catalogtrust.model.OrderRecord;
import com.catalogtrust.model.TrustScoringResult;
import com.catalogtrust.repository.AuditLogCsvRepository;
import com.catalogtrust.repository.CatalogCsvRepository;
import com.catalogtrust.repository.OrderCsvRepository;
import com.catalogtrust.service.ProjectDataPaths;
import com.catalogtrust.service.TrustScoringService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * MCP tool that verifies a catalog product using the shared trust-scoring pipeline.
 */
@Service
public class ListingTrustMcpTool {
    private final ProjectDataPaths paths;
    private final TrustScoringService scoringService;
    private final AuditLogCsvRepository auditLogRepository;

    public ListingTrustMcpTool(
            ProjectDataPaths paths,
            TrustScoringService scoringService,
            AuditLogCsvRepository auditLogRepository
    ) {
        this.paths = paths;
        this.scoringService = scoringService;
        this.auditLogRepository = auditLogRepository;
    }

    @Tool(
            name = "verify_listing_trust",
            description = "Evaluate a catalog listing by its exact, case-sensitive product ID. "
                    + "Returns the trust score, status, permitted action, and reasons. REVIEW and BLOCK "
                    + "results must not be used to authorize a purchase."
    )
    public Map<String, Object> verifyListingTrust(
            @ToolParam(description = "Exact catalog product ID, for example TEST-A")
            String product_id
    ) throws IOException {
        if (product_id == null || product_id.isBlank()) {
            throw new IllegalArgumentException("product_id is required and must not be blank");
        }

        List<CatalogSnapshot> catalog =
                new CatalogCsvRepository(paths.dataFile("catalog.csv")).findAll();
        List<OrderRecord> orders =
                new OrderCsvRepository(paths.dataFile("orders.csv")).findAll();

        TrustScoringResult result;
        try {
            result = scoringService.evaluateProduct(product_id, catalog, orders);
        } catch (IllegalArgumentException exception) {
            if ("product_id not found".equals(exception.getMessage())) {
                return Map.of("error", "product_id not found");
            }
            throw exception;
        }

        auditLogRepository.appendVerification(
                paths.auditLogFile(),
                result.productId(),
                result.productName(),
                result.trustScore(),
                result.status(),
                result.action(),
                String.join("; ", result.reasons())
        );

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("product_id", result.productId());
        response.put("product_name", result.productName());
        response.put("trust_score", result.trustScore());
        response.put("status", result.status());
        response.put("action", result.action());
        response.put("reasons", result.reasons());
        return response;
    }
}
