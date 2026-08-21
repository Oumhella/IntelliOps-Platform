package org.example.lead_service.client;

import org.example.lead_service.config.StockFeignClientConfig;
import org.example.lead_service.dto.StockProductDTO;
import org.example.lead_service.dto.StockInventoryDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "stock-service", configuration = StockFeignClientConfig.class)
public interface StockClient {

    @GetMapping("/api/v1/produits/{idProduit}/catalog")
    StockProductDTO obtenirProduit(@PathVariable("idProduit") Long idProduit);

    @GetMapping("/api/v1/boutiques/{idBoutique}")
    Object obtenirBoutique(@PathVariable("idBoutique") Long idBoutique);

    @PostMapping("/api/v1/inventaires/boutiques/{idBoutique}/produits/{idProduit}/reserver")
    void reserverStock(
            @PathVariable("idBoutique") Long idBoutique,
            @PathVariable("idProduit") Long idProduit,
            @RequestBody ReservationRequest request
    );

    @PostMapping("/api/v1/inventaires/boutiques/{idBoutique}/produits/{idProduit}/liberer")
    void libererStock(
            @PathVariable("idBoutique") Long idBoutique,
            @PathVariable("idProduit") Long idProduit,
            @RequestBody ReservationRequest request
    );

    @PostMapping("/api/v1/inventaires/boutiques/{idBoutique}/produits/{idProduit}/consommer")
    void consommerStock(
            @PathVariable("idBoutique") Long idBoutique,
            @PathVariable("idProduit") Long idProduit,
            @RequestBody ReservationRequest request
    );

    @GetMapping("/api/v1/inventaires/boutiques/{idBoutique}/produits/{idProduit}")
    StockInventoryDTO obtenirInventaire(
            @PathVariable("idBoutique") Long idBoutique,
            @PathVariable("idProduit") Long idProduit
    );

    record ReservationRequest(int quantite, String referenceOperation) {
    }
}
