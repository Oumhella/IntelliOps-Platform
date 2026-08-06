package org.example.lead_service.controller;

import org.example.lead_service.entity.StatutCommande;
import org.example.lead_service.dto.CommandeDTO;
import org.example.lead_service.service.CommandeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.example.common.dto.PageResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;

@RestController
@RequestMapping("/api/v1/commandes")
@RequiredArgsConstructor
public class CommandeController {

    private final CommandeService commandeService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'CSM', 'LOGISTIC')")
    public ResponseEntity<PageResponse<CommandeDTO>> rechercherCommandes(
            @RequestParam(required = false) StatutCommande statut,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(commandeService.rechercherCommandes(statut, page, size));
    }

    @GetMapping("/{idCommande}")
    @PreAuthorize("hasAnyRole('ADMIN', 'CSM', 'LOGISTIC')")
    public ResponseEntity<CommandeDTO> obtenirCommandeParId(@PathVariable Long idCommande) {
        return ResponseEntity.ok(commandeService.obtenirCommandeParId(idCommande));
    }

    @PostMapping("/{idCommande}/lignes")
    @PreAuthorize("hasRole('CSM')")
    public ResponseEntity<CommandeDTO> ajouterProduitACommande(
            @PathVariable Long idCommande,
            @RequestParam Long produitId,
            @RequestParam int quantite,
            @RequestParam double prixUnitaire) {

        return ResponseEntity.ok(commandeService.ajouterProduitACommande(idCommande, produitId, quantite, prixUnitaire));
    }

    @PutMapping("/{idCommande}/statut")
    @PreAuthorize("hasAnyRole('CSM', 'LOGISTIC')")
    public ResponseEntity<CommandeDTO> changerStatutCommande(
            @PathVariable Long idCommande,
            @RequestParam StatutCommande nouveauStatut,
            Authentication authentication) {

        boolean csmAction = hasRole(authentication, "ROLE_CSM")
                && (nouveauStatut == StatutCommande.CONFIRMEE || nouveauStatut == StatutCommande.ANNULEE);
        boolean logisticAction = hasRole(authentication, "ROLE_LOGISTIC")
                && (nouveauStatut == StatutCommande.PREPARATION
                || nouveauStatut == StatutCommande.EXPEDIEE
                || nouveauStatut == StatutCommande.LIVREE
                || nouveauStatut == StatutCommande.RETOURNEE
                || nouveauStatut == StatutCommande.ANNULEE);
        if (!csmAction && !logisticAction) {
            throw new AccessDeniedException("This order status is not owned by the current role.");
        }

        return ResponseEntity.ok(commandeService.changerStatutCommande(idCommande, nouveauStatut));
    }

    private boolean hasRole(Authentication authentication, String role) {
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> role.equals(authority.getAuthority()));
    }
}
