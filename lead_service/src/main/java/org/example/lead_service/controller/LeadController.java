package org.example.lead_service.controller;

import org.example.lead_service.entity.StatutLead;
import org.example.lead_service.entity.TypeInteraction;
import org.example.lead_service.dto.CommandeDTO;
import org.example.lead_service.dto.LeadDTO;
import org.example.lead_service.dto.NoteInteractionDTO;
import org.example.lead_service.service.LeadService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/leads")
@RequiredArgsConstructor
public class LeadController {

    private final LeadService leadService;

    @PostMapping
    public ResponseEntity<LeadDTO> creerLead(@RequestBody LeadDTO leadDTO) {
        return new ResponseEntity<>(leadService.creerLead(leadDTO), HttpStatus.CREATED);
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
    public ResponseEntity<NoteInteractionDTO> enregistrerInteraction(
            @PathVariable Long idLead,
            @RequestParam TypeInteraction type,
            @RequestParam(required = false) StatutLead nouveauStatut,
            @RequestBody String commentaire) {

        NoteInteractionDTO note = leadService.enregistrerInteraction(idLead, type, commentaire, nouveauStatut);
        return new ResponseEntity<>(note, HttpStatus.CREATED);
    }

    @PostMapping("/{idLead}/convertir")
    public ResponseEntity<CommandeDTO> convertirEnCommande(@PathVariable Long idLead) {
        return new ResponseEntity<>(leadService.convertirEnCommande(idLead), HttpStatus.CREATED);
    }
}