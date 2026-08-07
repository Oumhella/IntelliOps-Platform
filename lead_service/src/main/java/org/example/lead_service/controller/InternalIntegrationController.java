package org.example.lead_service.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.lead_service.dto.CommandeDTO;
import org.example.lead_service.dto.ExternalOrderImportRequest;
import org.example.lead_service.dto.ExternalOrderStateRequest;
import org.example.lead_service.service.LeadService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/internal/integrations")
@RequiredArgsConstructor
public class InternalIntegrationController {
    private final LeadService leadService;

    @PostMapping("/orders")
    @PreAuthorize("hasRole('INTEGRATION_SERVICE')")
    public ResponseEntity<CommandeDTO> importOrder(@Valid @RequestBody ExternalOrderImportRequest request) {
        return new ResponseEntity<>(leadService.importExternalOrder(request), HttpStatus.CREATED);
    }

    @PatchMapping("/orders/state")
    @PreAuthorize("hasRole('INTEGRATION_SERVICE')")
    public ResponseEntity<CommandeDTO> syncOrderState(@Valid @RequestBody ExternalOrderStateRequest request) {
        return ResponseEntity.ok(leadService.syncExternalOrderState(request));
    }
}
