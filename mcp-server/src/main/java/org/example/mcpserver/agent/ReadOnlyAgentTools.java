package org.example.mcpserver.agent;

import org.example.mcpserver.tools.LeadMcpTools;
import org.example.mcpserver.tools.StockMcpTools;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * Deliberately separate from the MCP mutation tools. This is the complete tool
 * allow-list available to the conversational endpoint.
 */
@Component
public class ReadOnlyAgentTools {
    private final StockMcpTools stockTools;
    private final LeadMcpTools leadTools;

    public ReadOnlyAgentTools(StockMcpTools stockTools, LeadMcpTools leadTools) {
        this.stockTools = stockTools;
        this.leadTools = leadTools;
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
}
