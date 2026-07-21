package org.example.mcpserver.tools;

import org.example.mcpserver.approval.ApprovalService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class StockMcpTools {

    private final RestClient stockServiceClient;
    private final ApprovalService approvalService;

    public StockMcpTools(RestClient stockServiceClient, ApprovalService approvalService) {
        this.stockServiceClient = stockServiceClient;
        this.approvalService = approvalService;
    }

    public record UpdateStockRequest(int quantite, String typeMouvement) { }
    public record StockAdjustment(Long boutiqueId, Long produitId, int quantite, String typeMouvement) { }

    @Tool(description = "Read-only: returns the current inventory of one product in one store. Use this before proposing an adjustment.")
    public String consulterInventaire(
            @ToolParam(description = "Store ID") Long idBoutique,
            @ToolParam(description = "Product ID") Long idProduit) {
        return stockServiceClient.get()
                .uri("/api/v1/inventaires/boutiques/{idBoutique}/produits/{idProduit}", idBoutique, idProduit)
                .retrieve().body(String.class);
    }

    @Tool(description = "Read-only: lists products in the ERP catalog for product discovery and inventory investigation.")
    public String listerProduits() {
        return stockServiceClient.get().uri("/api/v1/produits").retrieve().body(String.class);
    }

    @Tool(description = "PREVIEW ONLY. It never changes stock. Returns a short-lived approval token and the exact stock adjustment that would be made. Present this preview to a human and wait for an explicit confirmation.")
    public ApprovalService.ActionPreview preparerAjustementStock(
            @ToolParam(description = "Store ID") Long idBoutique,
            @ToolParam(description = "Product ID") Long idProduit,
            @ToolParam(description = "Quantity to add (positive) or remove (negative)") int quantite,
            @ToolParam(description = "Movement type: REASSORT, VENTE, RETOUR, PERTE, or AJUSTEMENT") String typeMouvement) {
        StockAdjustment adjustment = new StockAdjustment(idBoutique, idProduit, quantite, typeMouvement);
        return approvalService.prepare("STOCK_ADJUSTMENT", adjustment,
                "Adjust product %d in store %d by %+d (%s)".formatted(idProduit, idBoutique, quantite, typeMouvement));
    }

    @Tool(description = "EXECUTION STEP. Executes only a previously previewed stock adjustment. Call this only after a human explicitly confirms the displayed approval token. The confirmation text must be exactly CONFIRM.")
    public String confirmerAjustementStock(
            @ToolParam(description = "Approval token returned by preparerAjustementStock") String approvalToken,
            @ToolParam(description = "Must be exactly CONFIRM, after the human reviewed the preview") String confirmation) {
        StockAdjustment adjustment = approvalService.confirm(approvalToken, "STOCK_ADJUSTMENT", confirmation, StockAdjustment.class);
        return stockServiceClient.patch()
                .uri("/api/v1/inventaires/boutiques/{idBoutique}/produits/{idProduit}/ajuster", adjustment.boutiqueId(), adjustment.produitId())
                .body(new UpdateStockRequest(adjustment.quantite(), adjustment.typeMouvement()))
                .retrieve().body(String.class);
    }
}
