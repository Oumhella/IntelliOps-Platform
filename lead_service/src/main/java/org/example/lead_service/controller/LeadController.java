package org.example.lead_service.controller;

import org.example.lead_service.dto.*;
import org.example.lead_service.entity.StatutLead;
import org.example.lead_service.entity.TypeInteraction;
import org.example.lead_service.service.LeadService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;import org.springframework.web.bind.annotation.*;

import java.util.List;

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
    public ResponseEntity<LeadDTO> obtenirLeadParId(@PathVariable Long idLead) {
        return ResponseEntity.ok(leadService.obtenirLeadParId(idLead));
    }

    @GetMapping("/agent/{agentId}")
    public ResponseEntity<List<LeadDTO>> obtenirLeadsParAgent(@PathVariable Long agentId) {
        return ResponseEntity.ok(leadService.obtenirLeadsParAgent(agentId));
    }

    @PutMapping("/{idLead}/assigner")
    public ResponseEntity<LeadDTO> assignerAgent(@PathVariable Long idLead, @RequestParam Long agentId) {
        return ResponseEntity.ok(leadService.assignerAgent(idLead, agentId));
    }

    @PostMapping("/{idLead}/interactions")
    @PreAuthorize("hasRole('CSM')")
    public ResponseEntity<NoteInteractionDTO> enregistrerInteraction(
            @PathVariable Long idLead,
            @RequestBody NoteInteractionRequest requestDto) {

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
    public ResponseEntity<CommandeDTO> convertirEnCommande(
            @PathVariable Long idLead,
            @RequestBody CreationCommandeRequest request) { // 📥 On récupère le JSON ici !

        return new ResponseEntity<>(leadService.convertirEnCommande(idLead, request), HttpStatus.CREATED);
    }
}