package org.example.mcpserver.agent;

import org.example.mcpserver.tools.LeadMcpTools;
import org.example.mcpserver.tools.OpenApiMcpTools;
import org.example.mcpserver.tools.StockMcpTools;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Deliberately separate from the MCP mutation tools. This is the complete tool
 * allow-list available to the conversational endpoint.
 */
@Component
public class ReadOnlyAgentTools {
    private final StockMcpTools stockTools;
    private final LeadMcpTools leadTools;
    private final OpenApiMcpTools openApiTools;

    public ReadOnlyAgentTools(StockMcpTools stockTools, LeadMcpTools leadTools, OpenApiMcpTools openApiTools) {
        this.stockTools = stockTools;
        this.leadTools = leadTools;
        this.openApiTools = openApiTools;
    }

    @Tool(description = "Read-only: retrieve the current inventory for a product in a store before giving operational advice.")
    public String getInventory(
            @ToolParam(description = "Store ID") Long storeId,
            @ToolParam(description = "Product ID") Long productId) {
        return stockTools.consulterInventaire(storeId, productId);
    }

    @Tool(description = "Read-only: list the ERP product catalog for product discovery and inventory analysis.")
    public String listProducts() {
        return stockTools.listerProduits();
    }

    @Tool(description = "Read-only: retrieve one CRM lead and its current status.")
    public String getLead(@ToolParam(description = "Lead ID") Long leadId) {
        return leadTools.consulterLead(leadId);
    }

    @Tool(description = "Read-only: list the leads assigned to a CSM agent for workload and follow-up analysis.")
    public String listAgentLeads(@ToolParam(description = "CSM agent ID") Long agentId) {
        return leadTools.listerLeadsAgent(agentId);
    }

    @Tool(description = "Read-only: list the currently documented Swagger operations across user, stock, lead, abonnement, paiement, delivery, and notification services. Select only an operation whose readOnly field is true, then call executeOpenApiRead.")
    public String listOpenApiReadOperations() {
        return openApiTools.listerOperationsOpenApi();
    }

    @Tool(description = "Read-only: execute any documented GET endpoint through the API gateway. First call listOpenApiReadOperations, choose an operation with readOnly=true, then pass its service, operationId, and exact Swagger path/query parameters.")
    public String executeOpenApiRead(
            @ToolParam(description = "Service returned by listOpenApiReadOperations") String service,
            @ToolParam(description = "operationId returned by listOpenApiReadOperations") String operationId,
            @ToolParam(description = "Path parameters keyed by Swagger parameter name, or an empty object") Map<String, String> pathParameters,
            @ToolParam(description = "Query parameters keyed by Swagger parameter name, or an empty object") Map<String, String> queryParameters) {
        return openApiTools.executerLectureOpenApi(service, operationId, pathParameters, queryParameters);
    }
}
