package com.catalogtrust.controller;

import com.catalogtrust.model.ProductApiResponse;
import com.catalogtrust.model.BuyRequest;
import com.catalogtrust.agent.ShoppingAgentService;
import com.catalogtrust.service.CatalogApiService;
import com.catalogtrust.service.PurchaseService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Provides catalog endpoints used by the dashboard and storefront.
 */
@RestController
@RequestMapping("/api")
public class CatalogApiController {
    private final CatalogApiService catalogApiService;
    private final PurchaseService purchaseService;
    private final ShoppingAgentService shoppingAgentService;

    public CatalogApiController(
            CatalogApiService catalogApiService,
            PurchaseService purchaseService,
            ShoppingAgentService shoppingAgentService
    ) {
        this.catalogApiService = catalogApiService;
        this.purchaseService = purchaseService;
        this.shoppingAgentService = shoppingAgentService;
    }

    @GetMapping({"/products", "/shop/products"})
    public ResponseEntity<?> getProducts(
            @RequestParam(value = "product_id", required = false) String productId
    ) throws IOException {
        if (productId == null) {
            return ResponseEntity.ok(catalogApiService.getProducts());
        }
        return lookupProduct(productId);
    }

    @GetMapping("/products/{productId}")
    public ResponseEntity<?> getProduct(@PathVariable String productId) throws IOException {
        return lookupProduct(productId);
    }

    private ResponseEntity<?> lookupProduct(String productId) throws IOException {
        String normalizedId = productId.trim();
        if (normalizedId.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("detail", "product_id is required"));
        }

        ProductApiResponse product = catalogApiService.getProduct(normalizedId);
        if (product == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("detail", "product_id not found"));
        }
        return ResponseEntity.ok(product);
    }

    @GetMapping("/audit-log")
    public List<Map<String, Object>> getAuditLog() throws IOException {
        return catalogApiService.getAuditLog();
    }

    @PostMapping("/buy")
    public ResponseEntity<?> buyProduct(@RequestBody(required = false) BuyRequest request) throws IOException {
        if (request == null || request.productId() == null) {
            return ResponseEntity.unprocessableEntity().body(Map.of(
                    "detail", List.of(Map.of(
                            "type", "missing",
                            "loc", List.of("body", "product_id"),
                            "msg", "Field required",
                            "input", Map.of()
                    ))
            ));
        }
        return ResponseEntity.ok(purchaseService.buy(request.productId().strip()));
    }

    @GetMapping(value = "/agent/run", produces = "text/event-stream")
    public SseEmitter runShoppingAgent(@RequestParam(value = "query", defaultValue = "") String query) {
        return streamAgentEvents(() -> shoppingAgentService.runQuery(query));
    }

    @GetMapping(value = "/agent/demo", produces = "text/event-stream")
    public SseEmitter runAgentDemo() {
        return streamAgentEvents(shoppingAgentService::demoEvents);
    }

    @PostMapping("/agent/shop")
    public ResponseEntity<?> shopWithAgent(@RequestBody(required = false) AgentShopRequest request)
            throws IOException {
        if (request == null || request.goal() == null || request.goal().strip().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("detail", "Goal is required."));
        }
        return ResponseEntity.ok(shoppingAgentService.runShopGoal(request.goal()));
    }

    private SseEmitter streamAgentEvents(AgentEventSupplier eventSupplier) {
        SseEmitter emitter = new SseEmitter(0L);
        try {
            for (Map<String, Object> event : eventSupplier.get()) {
                emitter.send(SseEmitter.event().data(event));
            }
            emitter.complete();
        } catch (Exception exception) {
            try {
                emitter.send(SseEmitter.event().data(Map.of(
                        "step", "error",
                        "message", "Agent execution failed: " + exception.getMessage()
                )));
            } catch (IOException sendException) {
                emitter.completeWithError(sendException);
                return emitter;
            }
            emitter.complete();
        }
        return emitter;
    }

    @FunctionalInterface
    private interface AgentEventSupplier {
        List<Map<String, Object>> get() throws Exception;
    }

    public record AgentShopRequest(String goal) {
    }
}
