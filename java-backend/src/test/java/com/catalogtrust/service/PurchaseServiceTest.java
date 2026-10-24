package com.catalogtrust.service;

import com.catalogtrust.model.TrustScoringResult;
import com.catalogtrust.payment.RazorpayOrderResponse;
import com.catalogtrust.payment.RazorpayService;
import com.catalogtrust.repository.AuditLogCsvRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PurchaseServiceTest {
    @TempDir
    Path temporaryDirectory;

    private ProjectDataPaths paths;
    private RazorpayService razorpayService;
    private AuditLogCsvRepository auditRepository;
    private PurchaseService purchaseService;

    @BeforeEach
    void setUp() throws IOException {
        paths = mock(ProjectDataPaths.class);
        razorpayService = mock(RazorpayService.class);
        auditRepository = mock(AuditLogCsvRepository.class);
        Path catalogFile = temporaryDirectory.resolve("catalog.csv");
        Path ordersFile = temporaryDirectory.resolve("orders.csv");
        Files.writeString(catalogFile, """
                product_id,product_name,category,price,stock,last_updated,date_added
                SAFE-1,Safe Lamp,Home & Kitchen,25.50,10,2026-10-24T12:00:00+00:00,2026-10-01T12:00:00+00:00
                """);
        Files.writeString(ordersFile, """
                order_id,product_id,quantity,timestamp,status
                ORD-1,SAFE-1,1,2026-10-24T11:00:00+00:00,completed
                """);
        when(paths.dataFile("catalog.csv")).thenReturn(catalogFile);
        when(paths.dataFile("orders.csv")).thenReturn(ordersFile);
        purchaseService = new PurchaseService(
                paths,
                new TrustScoringService(),
                razorpayService,
                auditRepository
        );
    }

    @Test
    void verifiesProductBeforeCreatingOrderFromCatalogPrice() throws IOException {
        when(razorpayService.createTestOrder("Safe Lamp", new BigDecimal("25.5")))
                .thenReturn(new RazorpayOrderResponse("order_test_safe", 2550L, "INR", "created"));

        Map<String, Object> result = purchaseService.buy("SAFE-1");

        assertEquals("ALLOW", result.get("action"));
        assertEquals("ORDER_CREATED", result.get("purchase_status"));
        assertEquals("order_test_safe", result.get("order_id"));
        assertEquals(100, result.get("trust_score"));
        verify(razorpayService).createTestOrder("Safe Lamp", new BigDecimal("25.5"));
        verify(auditRepository).appendVerification(
                eq(paths.auditLogFile()),
                eq("SAFE-1"),
                eq("Safe Lamp"),
                eq(100),
                eq("VERIFIED"),
                eq("ALLOW"),
                eq("")
        );
    }

    @Test
    void allowsWarningProductsToCreateTestOrders() throws IOException {
        Path warningCatalog = temporaryDirectory.resolve("warning-catalog.csv");
        Path warningOrders = temporaryDirectory.resolve("warning-orders.csv");
        Files.writeString(warningCatalog, """
                product_id,product_name,category,price,stock,last_updated,date_added
                WARN-1,Warning Lamp,Home & Kitchen,25.50,10,2026-10-24T12:00:00+00:00,2026-10-01T12:00:00+00:00
                """);
        Files.writeString(warningOrders, """
                order_id,product_id,quantity,timestamp,status
                ORD-1,WARN-1,8,2026-10-24T11:00:00+00:00,completed
                """);
        when(paths.dataFile("catalog.csv")).thenReturn(warningCatalog);
        when(paths.dataFile("orders.csv")).thenReturn(warningOrders);
        when(razorpayService.createTestOrder("Warning Lamp", new BigDecimal("25.5")))
                .thenReturn(new RazorpayOrderResponse("order_test_warning", 2550L, "INR", "created"));

        Map<String, Object> result = purchaseService.buy("WARN-1");

        assertEquals("ALLOW_WITH_WARNING", result.get("action"));
        assertEquals("ORDER_CREATED", result.get("purchase_status"));
        verify(razorpayService).createTestOrder("Warning Lamp", new BigDecimal("25.5"));
    }

    @Test
    void doesNotCallRazorpayForUntrustedProducts() throws IOException {
        Path staleCatalog = temporaryDirectory.resolve("blocked-catalog.csv");
        Files.writeString(staleCatalog, """
                product_id,product_name,category,price,stock,last_updated,date_added
                BLOCK-1,Old Lamp,Home & Kitchen,25.50,10,2026-10-18T12:00:00+00:00,2026-10-01T12:00:00+00:00
                LATEST,Latest Lamp,Home & Kitchen,50.00,10,2026-10-24T12:00:00+00:00,2026-10-01T12:00:00+00:00
                """);
        when(paths.dataFile("catalog.csv")).thenReturn(staleCatalog);

        Map<String, Object> result = purchaseService.buy("BLOCK-1");

        assertEquals("REVIEW", result.get("action"));
        assertEquals("BLOCKED_BY_TRUST_LAYER", result.get("purchase_status"));
        assertEquals(null, result.get("order_id"));
        verify(razorpayService, never()).createTestOrder(any(), any());
    }

    @Test
    void blocksProductsBeforeRazorpayWhenScoringReturnsBlock() throws IOException {
        Path blockedCatalog = temporaryDirectory.resolve("block-catalog.csv");
        Path blockedOrders = temporaryDirectory.resolve("block-orders.csv");
        Files.writeString(blockedCatalog, """
                product_id,product_name,category,price,stock,last_updated,date_added
                BLOCK-1,Blocked Lamp,Home & Kitchen,25.50,1,2026-10-20T12:00:00+00:00,2026-10-01T12:00:00+00:00
                LATEST,Latest Lamp,Home & Kitchen,50.00,10,2026-10-24T12:00:00+00:00,2026-10-01T12:00:00+00:00
                """);
        Files.writeString(blockedOrders, """
                order_id,product_id,quantity,timestamp,status
                ORD-1,BLOCK-1,2,2026-10-20T11:00:00+00:00,completed
                """);
        when(paths.dataFile("catalog.csv")).thenReturn(blockedCatalog);
        when(paths.dataFile("orders.csv")).thenReturn(blockedOrders);

        Map<String, Object> result = purchaseService.buy("BLOCK-1");

        assertEquals("BLOCK", result.get("action"));
        assertEquals("BLOCKED_BY_TRUST_LAYER", result.get("purchase_status"));
        verify(razorpayService, never()).createTestOrder(any(), any());
    }

    @Test
    void rejectsUnknownProductsWithoutCallingRazorpay() {
        assertThrows(ProductNotFoundException.class, () -> purchaseService.buy("UNKNOWN"));
        verify(razorpayService, never()).createTestOrder(any(), any());
    }

    @Test
    void turnsProviderFailureIntoEstablishedOrderFailedResponse() throws IOException {
        when(razorpayService.createTestOrder("Safe Lamp", new BigDecimal("25.5")))
                .thenThrow(new com.catalogtrust.payment.RazorpayPaymentException("Provider request failed"));

        Map<String, Object> result = purchaseService.buy("SAFE-1");

        assertEquals("ORDER_FAILED", result.get("purchase_status"));
        assertEquals(null, result.get("order_id"));
        assertEquals("Purchase verified but Razorpay order creation failed.", result.get("message"));
        verify(razorpayService).createTestOrder("Safe Lamp", new BigDecimal("25.5"));
    }
}
