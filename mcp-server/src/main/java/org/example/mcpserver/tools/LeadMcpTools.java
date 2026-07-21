package org.example.mcpserver.tools;

import org.example.mcpserver.approval.ApprovalService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;

@Component
public class LeadMcpTools {
    private final RestClient leadServiceClient;
    private final ApprovalService approvalService;

    public LeadMcpTools(RestClient leadServiceClient, ApprovalService approvalService) {
        this.leadServiceClient = leadServiceClient;
        this.approvalService = approvalService;
    }

    public record ItemRequest(Long productId, int quantity, double unitPrice) { }
    public record CreationCommandeRequest(double totalAmount, List<ItemRequest> items) { }
    public record LeadConversion(Long leadId, List<ItemRequest> items, double totalAmount) { }

    @Tool(description = "Read-only: returns the CRM lead, including its current status, before proposing an order conversion.")
    public String consulterLead(@ToolParam(description = "Lead ID") Long idLead) {
        return leadServiceClient.get().uri("/api/v1/leads/{idLead}", idLead).retrieve().body(String.class);
    }

    @Tool(description = "Read-only: lists leads assigned to a CSM agent, useful for a follow-up workload view.")
    public String listerLeadsAgent(@ToolParam(description = "CSM agent ID") Long agentId) {
        return leadServiceClient.get().uri("/api/v1/leads/agent/{agentId}", agentId).retrieve().body(String.class);
    }

    @Tool(description = "PREVIEW ONLY. It never creates an order. Calculates the total, returns a short-lived approval token, and describes the lead-to-order conversion. Present it to a human and wait for explicit confirmation.")
    public ApprovalService.ActionPreview preparerConversionLeadEnCommande(
            @ToolParam(description = "Qualified lead ID") Long idLead,
            @ToolParam(description = "Order lines: productId, quantity, and unitPrice") List<ItemRequest> items) {
        double total = items.stream().mapToDouble(item -> item.quantity() * item.unitPrice()).sum();
        LeadConversion conversion = new LeadConversion(idLead, List.copyOf(items), total);
        return approvalService.prepare("LEAD_CONVERSION", conversion,
                "Convert lead %d into an order with %d line(s), total %.2f".formatted(idLead, items.size(), total));
    }

    @Tool(description = "EXECUTION STEP. Creates an order only from a previously previewed conversion token. Call it only after a human explicitly confirms the displayed token. The confirmation text must be exactly CONFIRM.")
    public String confirmerConversionLeadEnCommande(
            @ToolParam(description = "Approval token returned by preparerConversionLeadEnCommande") String approvalToken,
            @ToolParam(description = "Must be exactly CONFIRM, after human review") String confirmation) {
        LeadConversion conversion = approvalService.confirm(approvalToken, "LEAD_CONVERSION", confirmation, LeadConversion.class);
        return leadServiceClient.post().uri("/api/v1/leads/{idLead}/convertir", conversion.leadId())
                .body(new CreationCommandeRequest(conversion.totalAmount(), conversion.items()))
                .retrieve().body(String.class);
    }
}
