package org.example.mcpserver.agent;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

/** Explicit read adapters for stable first-party ERP contracts. */
@Component
public class PlatformReadTools {
    private final RestClient gateway;

    public PlatformReadTools(@Qualifier("gatewayClient") RestClient gateway) {
        this.gateway = gateway;
    }

    public String listOrders() {
        return get("/api/v1/commandes?page=0&size=100");
    }

    public String getOrder(long id) {
        return get("/api/v1/commandes/" + id);
    }

    public String listDeliveries() {
        return get("/api/v1/livraisons?page=0&size=100");
    }

    public String getDelivery(long id) {
        return get("/api/v1/livraisons/" + id);
    }

    public String listPayments() {
        return get("/api/v1/payments?page=0&size=100");
    }

    public String getPayment(long id) {
        return get("/api/v1/payments/" + id);
    }

    public String listNotifications() {
        return get("/api/v1/notifications?page=0&size=100");
    }

    public String getNotification(long id) {
        return get("/api/v1/notifications/" + id);
    }

    public String currentSubscription() {
        return get("/api/v1/abonnements/entitlement");
    }

    private String get(String uri) {
        try {
            return gateway.get().uri(uri).retrieve().body(String.class);
        } catch (HttpClientErrorException exception) {
            throw new ResponseStatusException(exception.getStatusCode(),
                    exception.getStatusCode().value() == 403
                            ? "Your role is not permitted to view this resource."
                            : "The ERP rejected this read request.", exception);
        }
    }
}
