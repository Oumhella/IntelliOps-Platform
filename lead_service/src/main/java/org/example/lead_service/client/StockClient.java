package org.example.lead_service.client;

import org.example.common.feign.FeignClientConfig;
import org.example.lead_service.dto.StockProductDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "stock-service", configuration = FeignClientConfig.class)
public interface StockClient {

    @GetMapping("/api/v1/produits/{idProduit}")
    StockProductDTO obtenirProduit(@PathVariable("idProduit") Long idProduit);

    @GetMapping("/api/v1/boutiques/{idBoutique}")
    Object obtenirBoutique(@PathVariable("idBoutique") Long idBoutique);

    @PostMapping("/api/v1/inventaires/boutiques/{idBoutique}/produits/{idProduit}/reserver")
    void reserverStock(
            @PathVariable("idBoutique") Long idBoutique,
            @PathVariable("idProduit") Long idProduit,
            @RequestParam("quantite") int quantite
    );
}
