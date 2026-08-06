package org.example.lead_service.controller;

import org.example.lead_service.dto.*;
import org.example.lead_service.entity.StatutLead;
import org.example.lead_service.entity.TypeInteraction;
import org.example.lead_service.service.LeadService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import org.example.common.dto.PageResponse;

@RestController
@RequestMapping("/api/v1/leads")
@RequiredArgsConstructor
public class LeadController {

    private final LeadService leadService;

    @PostMapping
    @PreAuthorize("hasRole('CSM')")
    public ResponseEntity<LeadDTO> creerLead(
            @RequestBody LeadDTO leadDTO,
            @RequestAttribute("userId") Long agentId
    ) {
        leadDTO.setAgentId(agentId);
        return ResponseEntity.ok(leadService.creerLead(leadDTO));
    }

    @GetMapping("/{idLead}")
    @PreAuthorize("hasAnyRole('ADMIN', 'CSM')")
    public ResponseEntity<LeadDTO> obtenirLeadParId(
            @PathVariable Long idLead,
            @RequestAttribute("userId") Long currentUserId,
            Authentication authentication) {
        LeadDTO lead = leadService.obtenirLeadParId(idLead);
        assertLeadAccess(lead, currentUserId, authentication);
        return ResponseEntity.ok(lead);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'CSM')")
    public ResponseEntity<PageResponse<LeadDTO>> rechercherLeads(
            @RequestParam(required = false) StatutLead statut,
            @RequestParam(required = false) Long agentId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestAttribute("userId") Long currentUserId,
            Authentication authentication) {
        if (hasRole(authentication, "ROLE_CSM")) {
            agentId = currentUserId;
        }
        return ResponseEntity.ok(leadService.rechercherLeads(statut, agentId, page, size));
    }

    @GetMapping("/agent/{agentId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'CSM')")
    public ResponseEntity<List<LeadDTO>> obtenirLeadsParAgent(
            @PathVariable Long agentId,
            @RequestAttribute("userId") Long currentUserId,
            Authentication authentication) {
        if (hasRole(authentication, "ROLE_CSM") && !currentUserId.equals(agentId)) {
            throw new AccessDeniedException("CSM users can only view their own lead queue.");
        }
        return ResponseEntity.ok(leadService.obtenirLeadsParAgent(agentId));
    }

    @PutMapping("/{idLead}/assigner")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<LeadDTO> assignerAgent(@PathVariable Long idLead, @RequestParam Long agentId) {
        return ResponseEntity.ok(leadService.assignerAgent(idLead, agentId));
    }

    @PostMapping("/{idLead}/interactions")
    @PreAuthorize("hasRole('CSM')")
    public ResponseEntity<NoteInteractionDTO> enregistrerInteraction(
            @PathVariable Long idLead,
            @RequestBody NoteInteractionRequest requestDto,
            @RequestAttribute("userId") Long currentUserId,
            Authentication authentication) {

        assertLeadAccess(leadService.obtenirLeadParId(idLead), currentUserId, authentication);

        // On passe les données proprement extraites du JSON au service
        NoteInteractionDTO note = leadService.enregistrerInteraction(
                idLead,
                requestDto.getTypeInteraction(),
                requestDto.getCommentaireAgent(),
                requestDto.getNouveauStatut()
        );

        return new ResponseEntity<>(note, HttpStatus.CREATED);
    }

    @PostMapping("/{idLead}/convertir")
    @PreAuthorize("hasRole('CSM')")
    public ResponseEntity<CommandeDTO> convertirEnCommande(
            @PathVariable Long idLead,
            @RequestBody CreationCommandeRequest request) { // 📥 On récupère le JSON ici !

        return new ResponseEntity<>(leadService.convertirEnCommande(idLead, request), HttpStatus.CREATED);
    }

    private void assertLeadAccess(LeadDTO lead, Long currentUserId, Authentication authentication) {
        if (hasRole(authentication, "ROLE_CSM") && !currentUserId.equals(lead.getAgentId())) {
            throw new AccessDeniedException("This lead is assigned to another CSM user.");
        }
    }

    private boolean hasRole(Authentication authentication, String role) {
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> role.equals(authority.getAuthority()));
    }
}
