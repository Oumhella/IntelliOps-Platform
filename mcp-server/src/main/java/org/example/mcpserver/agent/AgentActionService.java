package org.example.mcpserver.agent;

import org.example.mcpserver.approval.ApprovalService;
import org.example.mcpserver.tools.LeadMcpTools;
import org.example.mcpserver.tools.OpenApiMcpTools;
import org.example.mcpserver.tools.StockMcpTools;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AgentActionService {
    private final ApprovalService approvalService;
    private final StockMcpTools stockTools;
    private final LeadMcpTools leadTools;
    private final OpenApiMcpTools openApiTools;

    public AgentActionService(ApprovalService approvalService, StockMcpTools stockTools,
                              LeadMcpTools leadTools, OpenApiMcpTools openApiTools) {
        this.approvalService = approvalService;
        this.stockTools = stockTools;
        this.leadTools = leadTools;
        this.openApiTools = openApiTools;
    }

    public ActionExecution confirm(String token) {
        String operation = approvalService.operationFor(token);
        String result = switch (operation) {
            case "STOCK_ADJUSTMENT" -> stockTools.confirmerAjustementStock(token, "CONFIRM");
            case "LEAD_CONVERSION" -> leadTools.confirmerConversionLeadEnCommande(token, "CONFIRM");
            case "OPENAPI_MUTATION" -> openApiTools.confirmerMutationOpenApi(token, "CONFIRM");
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "This pending operation is not executable from the assistant.");
        };
        return new ActionExecution(operation, successMessage(operation), result);
    }

    public ActionExecution reject(String token) {
        ApprovalService.ActionPreview preview = approvalService.reject(token);
        return new ActionExecution(preview.operation(), "Action rejected. No business data was changed.", null);
    }

    private String successMessage(String operation) {
        return switch (operation) {
            case "STOCK_ADJUSTMENT" -> "Stock adjustment completed successfully.";
            case "LEAD_CONVERSION" -> "Lead converted to an order successfully; authoritative prices and stock reservation were applied.";
            default -> "Operational action completed successfully.";
        };
    }

    public record ActionExecution(String operation, String message, String result) { }
}
