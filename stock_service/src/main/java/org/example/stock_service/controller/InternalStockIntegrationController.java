package org.example.stock_service.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.stock_service.dto.request.ExternalProductImportRequest;
import org.example.stock_service.dto.response.ExternalProductImportResponse;
import org.example.stock_service.service.ExternalCatalogImportService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/internal/integrations/catalog")
@RequiredArgsConstructor
@PreAuthorize("hasRole('INTEGRATION_SERVICE')")
public class InternalStockIntegrationController {
    private final ExternalCatalogImportService importService;

    @PostMapping("/products")
    @ResponseStatus(HttpStatus.CREATED)
    public ExternalProductImportResponse importProduct(@Valid @RequestBody ExternalProductImportRequest request) {
        return importService.importProduct(request);
    }
}
