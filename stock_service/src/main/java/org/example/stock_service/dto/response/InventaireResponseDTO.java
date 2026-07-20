package org.example.stock_service.dto.response;

import lombok.Data;
import java.util.List;

@Data
public class InventaireResponseDTO {
    private Long id;
    private int quantiteDisponible;
    private int quantiteReservee;
    private BoutiqueResponseDTO boutique;
    private ProduitResponseDTO produit;
    private RegleApprovisionnementResponseDTO regleApprovisionnement;
    private List<MouvementStockResponseDTO> mouvements;
}