package org.example.storeintegration.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.storeintegration.config.IntegrationProperties;
import org.example.storeintegration.dto.IntegrationDtos.WooAuthorizationCallback;
import org.example.storeintegration.entity.StoreConnection;
import org.example.storeintegration.service.IntegrationService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;
import java.net.URI;

@Slf4j
@RestController
@RequestMapping("/api/v1/integrations/oauth")
@RequiredArgsConstructor
public class OAuthController {
    private final IntegrationService service;
    private final IntegrationProperties properties;

    @GetMapping("/shopify/callback")
    public ResponseEntity<Void> shopify(@RequestParam MultiValueMap<String, String> query,
                                        @CookieValue(name = IntegrationController.SHOPIFY_STATE_COOKIE, required = false) String browserState) {
        log.info("Shopify OAuth callback received. shop={}, state present={}, browserState present={}",
                query.getFirst("shop"), query.containsKey("state"), browserState != null);
        StoreConnection connection = service.completeShopify(query, browserState);
        log.info("Shopify OAuth completed successfully. connectionId={}, store={}", connection.getId(), connection.getStoreUrl());
        String target = UriComponentsBuilder.fromUriString(properties.frontendReturnUrl())
                .queryParam("provider", "shopify").queryParam("connected", connection.getId()).build().encode().toUriString();
        ResponseCookie expired = IntegrationController.shopifyStateCookie("", 0);
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(target))
                .header(HttpHeaders.SET_COOKIE, expired.toString()).build();
    }

    @PostMapping("/woocommerce/callback")
    public ResponseEntity<Void> woocommerce(@Valid @RequestBody WooAuthorizationCallback callback) {
        service.completeWooCommerce(callback);
        return ResponseEntity.noContent().build();
    }
}
