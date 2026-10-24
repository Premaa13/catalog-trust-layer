package com.catalogtrust.mcp;

import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Exposes the annotated trust-verification method to Spring AI's MCP server.
 */
@Configuration
public class McpToolConfiguration {
    @Bean
    public ToolCallbackProvider listingTrustToolCallbackProvider(ListingTrustMcpTool tool) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(tool)
                .build();
    }
}
