package org.example.lead_service.controller;

import org.example.lead_service.entity.StatutCommande;
import org.example.lead_service.dto.CommandeDTO;
import org.example.lead_service.service.CommandeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/commandes")
@RequiredArgsConstructor
public class CommandeController {

    private final CommandeService commandeService;

    @GetMapping("/{idCommande}")
    public ResponseEntity<CommandeDTO> obtenirCommandeParId(@PathVariable Long idCommande) {
        return ResponseEntity.ok(commandeService.obtenirCommandeParId(idCommande));
    }

    @PostMapping("/{idCommande}/lignes")
    public ResponseEntity<CommandeDTO> ajouterProduitACommande(
            @PathVariable Long idCommande,
            @RequestParam Long produitId,
            @RequestParam int quantite,
            @RequestParam double prixUnitaire) {

        return ResponseEntity.ok(commandeService.ajouterProduitACommande(idCommande, produitId, quantite, prixUnitaire));
    }

    @PutMapping("/{idCommande}/statut")
    public ResponseEntity<CommandeDTO> changerStatutCommande(
            @PathVariable Long idCommande,
            @RequestParam StatutCommande nouveauStatut) {

        return ResponseEntity.ok(commandeService.changerStatutCommande(idCommande, nouveauStatut));
    }
}