package com.catalogtrust.controller;

import com.catalogtrust.config.StaticFileConfiguration;
import com.catalogtrust.model.ProductApiResponse;
import com.catalogtrust.agent.ShoppingAgentService;
import com.catalogtrust.service.CatalogApiService;
import com.catalogtrust.service.PurchaseService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        value = {CatalogApiController.class, ApiExceptionHandler.class},
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = StaticFileConfiguration.class
        )
)
class CatalogApiControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CatalogApiService catalogApiService;

    @MockitoBean
    private PurchaseService purchaseService;

    @MockitoBean
    private ShoppingAgentService shoppingAgentService;

    @Test
    void returnsProductsInSnakeCaseForBothExistingFrontendRoutes() throws Exception {
        ProductApiResponse testProduct = product("TEST-A", true);
        when(catalogApiService.getProducts()).thenReturn(List.of(testProduct));

        mockMvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].product_id").value("TEST-A"))
                .andExpect(jsonPath("$[0].product_name").value("Earbuds"))
                .andExpect(jsonPath("$[0].trust_score").value(50))
                .andExpect(jsonPath("$[0].is_test_case").value(true))
                .andExpect(jsonPath("$[0].category").value("Electronics"))
                .andExpect(jsonPath("$[0].date_added").value("2026-01-01T00:00:00+00:00"))
                .andExpect(jsonPath("$[0].price").value(25.5))
                .andExpect(jsonPath("$[0].stock").value(3))
                .andExpect(jsonPath("$[0].image_url").value("https://example.test/item.jpg"));

        mockMvc.perform(get("/api/shop/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].product_id").value("TEST-A"));
    }

    @Test
    void returnsOneProductAndReportsInvalidOrMissingIds() throws Exception {
        ProductApiResponse testProduct = product("TEST-A", true);
        when(catalogApiService.getProduct("TEST-A")).thenReturn(testProduct);
        when(catalogApiService.getProduct("UNKNOWN")).thenReturn(null);

        mockMvc.perform(get("/api/products/TEST-A"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.product_id").value("TEST-A"));

        mockMvc.perform(get("/api/products").queryParam("product_id", " "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("product_id is required"));

        mockMvc.perform(get("/api/products/UNKNOWN"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("product_id not found"));
    }

    @Test
    void returnsAuditLogEntriesAndEmptyList() throws Exception {
        when(catalogApiService.getAuditLog()).thenReturn(List.of(Map.of(
                "timestamp", "2026-10-24T10:00:00+00:00",
                "product_id", "TEST-D",
                "product_name", "Lamp",
                "trust_score", 100,
                "status", "VERIFIED",
                "action", "ALLOW",
                "reasons", ""
        )));

        mockMvc.perform(get("/api/audit-log").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].product_id").value("TEST-D"))
                .andExpect(jsonPath("$[0].trust_score").value(100))
                .andExpect(jsonPath("$[0].reasons").value(""));

        when(catalogApiService.getAuditLog()).thenReturn(List.of());
        mockMvc.perform(get("/api/audit-log"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void reportsDataFileErrorsAsJson() throws Exception {
        when(catalogApiService.getProducts()).thenThrow(new IOException("Catalog CSV file not found"));

        mockMvc.perform(get("/api/products"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.detail").value("Catalog CSV file not found"));
    }

    @Test
    void storefrontBuyEndpointReturnsVerifiedPaymentResponse() throws Exception {
        when(purchaseService.buy("SAFE-1")).thenReturn(Map.of(
                "product_id", "SAFE-1",
                "product_name", "Safe Lamp",
                "status", "VERIFIED",
                "action", "ALLOW",
                "trust_score", 100,
                "reasons", List.of(),
                "purchase_status", "ORDER_CREATED",
                "order_id", "order_test_safe",
                "message", "Purchase verified. Razorpay order created successfully. Order ID: order_test_safe"
        ));

        mockMvc.perform(post("/api/buy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"product_id\":\"SAFE-1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.purchase_status").value("ORDER_CREATED"))
                .andExpect(jsonPath("$.order_id").value("order_test_safe"))
                .andExpect(jsonPath("$.action").value("ALLOW"));

        verify(purchaseService).buy("SAFE-1");
    }

    @Test
    void storefrontBuyEndpointHandlesBadRequestsAndUnknownProducts() throws Exception {
        mockMvc.perform(post("/api/buy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail[0].loc[1]").value("product_id"));

        mockMvc.perform(post("/api/buy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail[0].type").value("json_invalid"));

        when(purchaseService.buy("UNKNOWN"))
                .thenThrow(new com.catalogtrust.service.ProductNotFoundException("UNKNOWN"));
        mockMvc.perform(post("/api/buy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"product_id\":\"UNKNOWN\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("product_id not found"));

        verify(purchaseService, never()).buy(" ");
    }

    @Test
    void agentShopRouteValidatesGoalAndReturnsCompatibleFlowShape() throws Exception {
        mockMvc.perform(post("/api/agent/shop")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"goal\":\"  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Goal is required."));

        when(shoppingAgentService.runShopGoal("find LED lamp")).thenReturn(Map.of(
                "goal", "find LED lamp",
                "events", List.of(Map.of("type", "search_result", "message", "Found 1 matching product(s).")),
                "final_result", Map.of("purchase_status", "BLOCKED_BY_TRUST_LAYER")
        ));
        mockMvc.perform(post("/api/agent/shop")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"goal\":\"find LED lamp\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.goal").value("find LED lamp"))
                .andExpect(jsonPath("$.events[0].type").value("search_result"))
                .andExpect(jsonPath("$.final_result.purchase_status").value("BLOCKED_BY_TRUST_LAYER"));
    }

    private static ProductApiResponse product(String productId, boolean isTestCase) {
        return new ProductApiResponse(
                productId,
                "Earbuds",
                50,
                "FLAGGED",
                "REVIEW",
                List.of("Price spike"),
                isTestCase,
                "Electronics",
                "2026-01-01T00:00:00+00:00",
                25.5,
                3,
                "https://example.test/item.jpg"
        );
    }
}
