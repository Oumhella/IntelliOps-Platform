package org.example.storeintegration.controller;

import lombok.RequiredArgsConstructor;
import org.example.storeintegration.entity.WebhookEvent;
import org.example.storeintegration.service.WebhookService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/integrations/webhooks")
@RequiredArgsConstructor
public class WebhookController {
    private final WebhookService service;

    @PostMapping("/shopify/{connectionId}")
    public ResponseEntity<Void> shopify(@PathVariable Long connectionId, @RequestBody byte[] payload,
                                        @RequestHeader("X-Shopify-Hmac-Sha256") String hmac,
                                        @RequestHeader("X-Shopify-Webhook-Id") String eventId,
                                        @RequestHeader("X-Shopify-Topic") String topic,
                                        @RequestHeader("X-Shopify-Shop-Domain") String shopDomain) {
        service.receiveShopify(connectionId, payload, hmac, eventId, topic, shopDomain);
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/woocommerce/{connectionId}")
    public ResponseEntity<Void> woocommerce(@PathVariable Long connectionId, @RequestBody byte[] payload,
                                            @RequestHeader("X-WC-Webhook-Signature") String signature,
                                            @RequestHeader("X-WC-Webhook-Delivery-ID") String deliveryId,
                                            @RequestHeader("X-WC-Webhook-Topic") String topic) {
        service.receiveWooCommerce(connectionId, payload, signature, deliveryId, topic);
        return ResponseEntity.accepted().build();
    }
}
