package org.example.storeintegration.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.storeintegration.dto.IntegrationDtos.*;
import org.example.storeintegration.service.IntegrationService;
import org.example.storeintegration.service.IntegrationService.StartedAuthorization;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/v1/integrations")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class IntegrationController {
    public static final String SHOPIFY_STATE_COOKIE = "intelliops_shopify_oauth_state";
    private final IntegrationService service;

    @GetMapping("/capabilities")
    public CapabilitiesResponse capabilities() {
        return service.capabilities();
    }

    @GetMapping("/connections")
    public List<ConnectionResponse> connections() {
        return service.connections();
    }

    @PostMapping("/shopify/connect")
    public ResponseEntity<AuthorizationResponse> connectShopify(@Valid @RequestBody ConnectRequest request) {
        StartedAuthorization started = service.beginShopify(request);
        ResponseCookie stateCookie = shopifyStateCookie(started.browserState(), 600);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, stateCookie.toString())
                .body(started.response());
    }

    static ResponseCookie shopifyStateCookie(String value, long maxAgeSeconds) {
        return ResponseCookie.from(SHOPIFY_STATE_COOKIE, value)
                .httpOnly(true).secure(true).sameSite("Lax")
                .path("/api/v1/integrations/oauth/shopify/callback").maxAge(maxAgeSeconds).build();
    }

    @PostMapping("/woocommerce/connect")
    public AuthorizationResponse connectWooCommerce(@Valid @RequestBody ConnectRequest request) {
        return service.beginWooCommerce(request);
    }

    @GetMapping("/connections/{id}/products")
    public List<ExternalProduct> products(@PathVariable Long id) {
        return service.externalProducts(id);
    }

    @GetMapping("/connections/{id}/mappings")
    public List<ProductMappingResponse> mappings(@PathVariable Long id) {
        return service.mappings(id);
    }

    @PostMapping("/connections/{id}/mappings")
    @ResponseStatus(HttpStatus.CREATED)
    public ProductMappingResponse map(@PathVariable Long id, @Valid @RequestBody ProductMappingRequest request) {
        return service.mapProduct(id, request);
    }

    @DeleteMapping("/mappings/{id}")
    public ResponseEntity<Void> deleteMapping(@PathVariable Long id) {
        service.deleteMapping(id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/connections/{id}")
    public ConnectionResponse disconnect(@PathVariable Long id) {
        return service.disconnect(id);
    }

    @GetMapping("/events")
    public List<EventResponse> events() {
        return service.recentEvents();
    }
}
