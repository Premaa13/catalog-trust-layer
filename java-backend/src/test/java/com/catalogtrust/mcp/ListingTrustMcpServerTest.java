package com.catalogtrust.mcp;

import com.catalogtrust.TrustEngineCommandLineRunner;
import com.catalogtrust.repository.AuditLogCsvRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.Map;
import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "server.shutdown=immediate"
)
class ListingTrustMcpServerTest {
    @LocalServerPort
    private int port;

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TestRestTemplate http;

    @MockitoBean
    private TrustEngineCommandLineRunner commandLineRunner;

    @MockitoBean
    private AuditLogCsvRepository auditLogRepository;

    @Test
    void registersTheExpectedToolAndProductIdSchema() {
        ToolCallbackProvider provider = applicationContext.getBean(ToolCallbackProvider.class);
        assertEquals(1, provider.getToolCallbacks().length);
        assertEquals("verify_listing_trust",
                provider.getToolCallbacks()[0].getToolDefinition().name());

        try (McpSyncClient client = newClient()) {
            McpSchema.ListToolsResult tools = client.listTools();
            assertEquals(1, tools.tools().size());
            assertEquals("verify_listing_trust", tools.tools().get(0).name());
            assertTrue(tools.tools().get(0).inputSchema().properties().containsKey("product_id"));
        }
    }

    @Test
    void clientCanInitializeDiscoverAndInvokeTheToolForReviewProduct() throws Exception {
        try (McpSyncClient client = newClient()) {
            assertTrue(client.isInitialized());
            McpSchema.CallToolResult result = client.callTool(new McpSchema.CallToolRequest(
                    "verify_listing_trust",
                    Map.of("product_id", "TEST-A")
            ));

            assertFalse(Boolean.TRUE.equals(result.isError()), result.content().toString());
            JsonNode response = responseJson(result);
            assertEquals("TEST-A", response.path("product_id").asText());
            assertEquals("Wireless Earbuds Pro", response.path("product_name").asText());
            assertEquals(50, response.path("trust_score").asInt());
            assertEquals("FLAGGED", response.path("status").asText());
            assertEquals("REVIEW", response.path("action").asText());
            assertTrue(response.path("reasons").get(0).asText()
                    .contains("Price increased 70.0% in 2.0 hours"));
        }
    }

    @Test
    void unknownProductUsesThePythonErrorObject() throws Exception {
        try (McpSyncClient client = newClient()) {
            McpSchema.CallToolResult result = client.callTool(new McpSchema.CallToolRequest(
                    "verify_listing_trust",
                    Map.of("product_id", "NOT-IN-CATALOG")
            ));

            JsonNode response = responseJson(result);
            assertEquals("product_id not found", response.path("error").asText());
        }
    }

    @Test
    void blankProductIdReturnsAnMcpToolError() {
        try (McpSyncClient client = newClient()) {
            McpSchema.CallToolResult result = client.callTool(new McpSchema.CallToolRequest(
                    "verify_listing_trust",
                    Map.of("product_id", " ")
            ));

            assertTrue(Boolean.TRUE.equals(result.isError()));
        }
    }

    @Test
    void servesDashboardStorefrontAndStaticFilesOverHttp() {
        ResponseEntity<String> dashboard = http.getForEntity("/", String.class);
        ResponseEntity<String> storefront = http.getForEntity("/shop", String.class);
        ResponseEntity<String> dashboardAsset = http.getForEntity("/static/index.html", String.class);
        ResponseEntity<String> storefrontAsset = http.getForEntity("/shop/static/index.html", String.class);

        assertEquals(200, dashboard.getStatusCode().value());
        assertTrue(dashboard.getBody().contains("Catalog Trust Layer Dashboard"));
        assertEquals(200, storefront.getStatusCode().value());
        assertTrue(storefront.getBody().contains("Trustmart | Shop"));
        assertEquals(200, dashboardAsset.getStatusCode().value());
        assertTrue(dashboardAsset.getBody().contains("Catalog Trust Layer Dashboard"));
        assertEquals(200, storefrontAsset.getStatusCode().value());
        assertTrue(storefrontAsset.getBody().contains("Trustmart | Shop"));
    }

    @Test
    void servesCatalogAuditAndPurchaseErrorRoutesOverHttp() throws Exception {
        ResponseEntity<String> products = http.getForEntity("/api/products", String.class);
        JsonNode productList = objectMapper.readTree(products.getBody());
        assertEquals(200, products.getStatusCode().value());
        assertEquals(70, productList.size());
        assertEquals("TEST-A", productList.get(0).path("product_id").asText());
        assertEquals("TEST-A", objectMapper.readTree(
                http.getForEntity("/api/products/TEST-A", String.class).getBody()
        ).path("product_id").asText());
        assertEquals("TEST-A", objectMapper.readTree(
                http.getForEntity("/api/products?product_id=TEST-A", String.class).getBody()
        ).path("product_id").asText());
        assertEquals(404, http.getForEntity("/api/products/UNKNOWN", String.class).getStatusCode().value());
        assertEquals(400, http.getForEntity(
                URI.create("http://localhost:" + port + "/api/products?product_id=%20"),
                String.class
        ).getStatusCode().value());
        assertEquals(200, http.getForEntity("/api/shop/products", String.class).getStatusCode().value());
        assertTrue(objectMapper.readTree(http.getForEntity("/api/audit-log", String.class).getBody()).isArray());
        assertEquals(404, http.postForEntity(
                "/api/buy",
                new HttpEntity<>("{\"product_id\":\"UNKNOWN\"}", jsonHeaders()),
                String.class
        ).getStatusCode().value());
    }

    @Test
    void streamsShoppingAgentEventsAndValidatesStructuredAgentRequests() throws Exception {
        ResponseEntity<String> runStream = http.getForEntity("/api/agent/run?query=TEST-D", String.class);
        ResponseEntity<String> demoStream = http.getForEntity("/api/agent/demo", String.class);

        assertEquals(200, runStream.getStatusCode().value());
        assertTrue(runStream.getHeaders().getContentType().toString().startsWith("text/event-stream"));
        assertTrue(runStream.getBody().contains("\"step\":\"understanding\""));
        assertTrue(runStream.getBody().contains("\"step\":\"search_complete\""));
        assertEquals(200, demoStream.getStatusCode().value());
        assertTrue(demoStream.getHeaders().getContentType().toString().startsWith("text/event-stream"));
        assertTrue(demoStream.getBody().contains("step"));

        ResponseEntity<String> invalidGoal = http.postForEntity(
                "/api/agent/shop",
                new HttpEntity<>("{\"goal\":\" \"}", jsonHeaders()),
                String.class
        );
        ResponseEntity<String> validGoal = http.postForEntity(
                "/api/agent/shop",
                new HttpEntity<>("{\"goal\":\"find a quantum flux capacitor\"}", jsonHeaders()),
                String.class
        );
        ResponseEntity<String> malformedRequest = http.postForEntity(
                "/api/agent/shop",
                new HttpEntity<>("{", jsonHeaders()),
                String.class
        );

        assertEquals(400, invalidGoal.getStatusCode().value());
        assertEquals(200, validGoal.getStatusCode().value());
        assertEquals("find a quantum flux capacitor",
                objectMapper.readTree(validGoal.getBody()).path("goal").asText());
        assertEquals(422, malformedRequest.getStatusCode().value());
    }

    private HttpHeaders jsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private McpSyncClient newClient() {
        HttpClientSseClientTransport transport =
                HttpClientSseClientTransport.builder("http://localhost:" + port)
                        .sseEndpoint("/sse")
                        .build();
        McpSyncClient client = McpClient.sync(transport).build();
        client.initialize();
        return client;
    }

    private JsonNode responseJson(McpSchema.CallToolResult result) throws Exception {
        assertFalse(result.content().isEmpty());
        McpSchema.TextContent textContent = (McpSchema.TextContent) result.content().get(0);
        return objectMapper.readTree(textContent.text());
    }
}
