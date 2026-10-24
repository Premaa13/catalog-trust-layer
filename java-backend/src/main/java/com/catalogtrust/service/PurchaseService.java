package com.catalogtrust.service;

import com.catalogtrust.model.CatalogSnapshot;
import com.catalogtrust.model.OrderRecord;
import com.catalogtrust.model.TrustScoringResult;
import com.catalogtrust.payment.RazorpayOrderResponse;
import com.catalogtrust.payment.RazorpayPaymentException;
import com.catalogtrust.payment.RazorpayService;
import com.catalogtrust.repository.AuditLogCsvRepository;
import com.catalogtrust.repository.CatalogCsvRepository;
import com.catalogtrust.repository.OrderCsvRepository;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Applies the trust decision before creating a test-mode Razorpay order.
 */
@Service
public class PurchaseService {
    private static final String BLOCKED = "BLOCKED_BY_TRUST_LAYER";
    private static final String ORDER_FAILED = "ORDER_FAILED";
    private static final String ORDER_CREATED = "ORDER_CREATED";

    private final ProjectDataPaths paths;
    private final TrustScoringService scoringService;
    private final RazorpayService razorpayService;
    private final AuditLogCsvRepository auditLogRepository;

    public PurchaseService(
            ProjectDataPaths paths,
            TrustScoringService scoringService,
            RazorpayService razorpayService,
            AuditLogCsvRepository auditLogRepository
    ) {
        this.paths = paths;
        this.scoringService = scoringService;
        this.razorpayService = razorpayService;
        this.auditLogRepository = auditLogRepository;
    }

    public Map<String, Object> buy(String productId) throws IOException {
        List<CatalogSnapshot> catalog = new CatalogCsvRepository(paths.dataFile("catalog.csv")).findAll();
        List<OrderRecord> orders = new OrderCsvRepository(paths.dataFile("orders.csv")).findAll();
        List<CatalogSnapshot> productSnapshots = catalog.stream()
                .filter(snapshot -> snapshot.productId().equals(productId))
                .toList();
        if (productSnapshots.isEmpty()) {
            throw new ProductNotFoundException(productId);
        }

        TrustScoringResult verification = scoringService.evaluateProduct(productId, catalog, orders);
        CatalogSnapshot latest = productSnapshots.stream()
                .max((left, right) -> left.lastUpdated().toInstant().compareTo(right.lastUpdated().toInstant()))
                .orElseThrow();

        if (!"ALLOW".equals(verification.action())
                && !"ALLOW_WITH_WARNING".equals(verification.action())) {
            Map<String, Object> result = responseBase(verification);
            result.put("purchase_status", BLOCKED);
            result.put("order_id", null);
            result.put("message", "Purchase blocked by the trust layer review policy.");
            logVerification(verification);
            return result;
        }

        String purchaseStatus = ORDER_FAILED;
        String orderId = null;
        String message = "Purchase verified but Razorpay order creation failed.";
        try {
            RazorpayOrderResponse order = razorpayService.createTestOrder(
                    latest.productName(),
                    BigDecimal.valueOf(latest.price())
            );
            if (order.orderId() != null && !order.orderId().isBlank()) {
                purchaseStatus = ORDER_CREATED;
                orderId = order.orderId();
                message = "Purchase verified. Razorpay order created successfully. Order ID: " + orderId;
            }
        } catch (IllegalArgumentException | RazorpayPaymentException exception) {
            // Keep the storefront's established response while omitting provider internals and credentials.
        }

        Map<String, Object> result = responseBase(verification);
        result.put("product_name", latest.productName());
        result.put("purchase_status", purchaseStatus);
        result.put("order_id", orderId);
        result.put("message", message);
        logVerification(verification);
        return result;
    }

    private Map<String, Object> responseBase(TrustScoringResult verification) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("product_id", verification.productId());
        response.put("product_name", verification.productName());
        response.put("status", verification.status());
        response.put("action", verification.action());
        response.put("trust_score", verification.trustScore());
        response.put("reasons", verification.reasons());
        return response;
    }

    private void logVerification(TrustScoringResult verification) throws IOException {
        auditLogRepository.appendVerification(
                paths.auditLogFile(),
                verification.productId(),
                verification.productName(),
                verification.trustScore(),
                verification.status(),
                verification.action(),
                String.join("; ", verification.reasons())
        );
    }
}
