package org.example.mcpserver.tools;

import org.example.mcpserver.approval.ApprovalService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.UUID;

@Component
public class LeadMcpTools {
    private final RestClient leadServiceClient;
    private final ApprovalService approvalService;

    public LeadMcpTools(RestClient leadServiceClient, ApprovalService approvalService) {
        this.leadServiceClient = leadServiceClient;
        this.approvalService = approvalService;
    }

    public record ItemRequest(Long productId, int quantity) { }
    public record CreationCommandeRequest(String idempotencyKey, Long stockLocationId, List<ItemRequest> items) { }
    public record LeadConversion(Long leadId, Long stockLocationId, List<ItemRequest> items,
                                 String idempotencyKey) { }

    @Tool(description = "Read-only: returns the CRM lead, including its current status, before proposing an order conversion.")
    public String consulterLead(@ToolParam(description = "Lead ID") Long idLead) {
        return leadServiceClient.get().uri("/api/v1/leads/{idLead}", idLead).retrieve().body(String.class);
    }

    @Tool(description = "Read-only: lists leads assigned to a CSM agent, useful for a follow-up workload view.")
    public String listerLeadsAgent(@ToolParam(description = "CSM agent ID") Long agentId) {
        return leadServiceClient.get().uri("/api/v1/leads/agent/{agentId}", agentId).retrieve().body(String.class);
    }

    @Tool(description = "Read-only: lists leads visible to the authenticated user. The lead service automatically limits a CSM to their own assigned queue.")
    public String listerLeadsVisibles() {
        return leadServiceClient.get().uri("/api/v1/leads?page=0&size=100").retrieve().body(String.class);
    }

    @Tool(description = "PREVIEW ONLY. It never creates an order. Returns a short-lived approval token for the selected fulfillment location and product quantities. The backend will apply authoritative catalog prices and reserve stock. Present it to a human and wait for explicit confirmation.")
    public ApprovalService.ActionPreview preparerConversionLeadEnCommande(
            @ToolParam(description = "Qualified lead ID") Long idLead,
            @ToolParam(description = "Stock location ID that will fulfill this order") Long stockLocationId,
            @ToolParam(description = "Order lines: productId and positive quantity") List<ItemRequest> items) {
        if (idLead == null || stockLocationId == null || items == null || items.isEmpty()
                || items.stream().anyMatch(item -> item == null || item.productId() == null || item.quantity() <= 0)) {
            throw new IllegalArgumentException("A lead, stock location, and positive product quantities are required.");
        }
        LeadConversion conversion = new LeadConversion(
                idLead, stockLocationId, List.copyOf(items), "mcp-" + UUID.randomUUID());
        return approvalService.prepare("LEAD_CONVERSION", conversion,
                "Convert lead %d into an order with %d line(s) fulfilled by location %d; catalog prices will be applied by the ERP"
                        .formatted(idLead, items.size(), stockLocationId));
    }

    @Tool(description = "EXECUTION STEP. Creates an order only from a previously previewed conversion token. Call it only after a human explicitly confirms the displayed token. The confirmation text must be exactly CONFIRM.")
    public String confirmerConversionLeadEnCommande(
            @ToolParam(description = "Approval token returned by preparerConversionLeadEnCommande") String approvalToken,
            @ToolParam(description = "Must be exactly CONFIRM, after human review") String confirmation) {
        LeadConversion conversion = approvalService.confirm(approvalToken, "LEAD_CONVERSION", confirmation, LeadConversion.class);
        return leadServiceClient.post().uri("/api/v1/leads/{idLead}/convertir", conversion.leadId())
                .body(new CreationCommandeRequest(
                        conversion.idempotencyKey(), conversion.stockLocationId(), conversion.items()))
                .retrieve().body(String.class);
    }
}
