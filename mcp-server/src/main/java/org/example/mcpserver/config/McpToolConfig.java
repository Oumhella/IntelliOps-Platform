package org.example.mcpserver.config;

import org.example.mcpserver.tools.LeadMcpTools;
import org.example.mcpserver.tools.StockMcpTools;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class McpToolConfig {

    @Bean
    public ToolCallbackProvider stockTools(StockMcpTools stockMcpTools) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(stockMcpTools)
                .build();
    }
    @Bean
    public ToolCallbackProvider leadTools(LeadMcpTools leadMcpTools) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(leadMcpTools)
                .build();
    }
}