package org.example.mcpserver.agent;

import org.example.mcpserver.approval.ApprovalService;
import org.example.mcpserver.tools.LeadMcpTools;
import org.example.mcpserver.tools.OpenApiMcpTools;
import org.example.mcpserver.tools.StockMcpTools;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Mutation allow-list for conversational use. These tools can only create an
 * approval preview. Confirmation tools are deliberately excluded so the model
 * can never approve or execute its own proposal.
 */
@Component
public class ActionPreviewAgentTools {
    private final StockMcpTools stockTools;
    private final LeadMcpTools leadTools;
    private final OpenApiMcpTools openApiTools;
    private final AgentActionIntentGuard intentGuard;

    public ActionPreviewAgentTools(StockMcpTools stockTools, LeadMcpTools leadTools,
                                   OpenApiMcpTools openApiTools, AgentActionIntentGuard intentGuard) {
        this.stockTools = stockTools;
        this.leadTools = leadTools;
        this.openApiTools = openApiTools;
        this.intentGuard = intentGuard;
    }

    @Tool(description = "Preview a manual stock adjustment. This never changes stock. The user must review and confirm the returned approval card.")
    public ApprovalService.ActionPreview previewStockAdjustment(
            @ToolParam(description = "Store or fulfillment location ID") Long storeId,
            @ToolParam(description = "Product ID") Long productId,
            @ToolParam(description = "Signed quantity: positive adds stock, negative removes it") int quantity,
            @ToolParam(description = "REASSORT, RETOUR, PERTE, or AJUSTEMENT") String movementType) {
        intentGuard.requireIdsPresent(storeId, productId);
        return stockTools.preparerAjustementStock(storeId, productId, quantity, movementType);
    }

    @Tool(description = "Preview converting a qualified lead into an order using authoritative catalog prices and stock reservation. This never creates the order until the user confirms the approval card.")
    public ApprovalService.ActionPreview previewLeadConversion(
            @ToolParam(description = "Qualified lead ID") Long leadId,
            @ToolParam(description = "Fulfillment stock location ID") Long stockLocationId,
            @ToolParam(description = "Order lines containing productId and positive quantity")
            List<LeadMcpTools.ItemRequest> items) {
        intentGuard.requireIdsPresent(leadId, stockLocationId);
        if (items != null) items.forEach(item -> intentGuard.requireIdsPresent(item.productId()));
        return leadTools.preparerConversionLeadEnCommande(leadId, stockLocationId, items);
    }

    @Tool(description = "List documented ERP operations, including write operations. Use this to discover the exact service and operationId before previewing a general operational change.")
    public String listAvailableOperations() {
        return openApiTools.listerOperationsOpenApi();
    }

    @Tool(description = "Preview any documented non-GET ERP operation. This sends nothing until the authenticated user confirms the approval card.")
    public ApprovalService.ActionPreview previewOpenApiMutation(
            @ToolParam(description = "Service returned by listAvailableOperations") String service,
            @ToolParam(description = "Non-GET operationId returned by listAvailableOperations") String operationId,
            @ToolParam(description = "Swagger path parameters or an empty object") Map<String, String> pathParameters,
            @ToolParam(description = "Swagger query parameters or an empty object") Map<String, String> queryParameters,
            @ToolParam(description = "Valid JSON request body, or an empty string") String requestBodyJson) {
        intentGuard.requireGenericMutation(operationId, pathParameters, queryParameters, requestBodyJson);
        return openApiTools.preparerMutationOpenApi(service, operationId, pathParameters, queryParameters, requestBodyJson);
    }
}
