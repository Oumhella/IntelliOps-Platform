package org.example.gateway_service.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Fails closed when the authenticated enterprise has no currently valid
 * subscription. Billing and platform-management routes intentionally do not
 * use this filter, so an administrator can still purchase a plan.
 */
@Component
public class SubscriptionEntitlementGatewayFilterFactory extends
        AbstractGatewayFilterFactory<SubscriptionEntitlementGatewayFilterFactory.Config> {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionEntitlementGatewayFilterFactory.class);
    private static final String ENTITLEMENT_URL =
            "http://ABONNEMENT-SERVICE/api/v1/abonnements/entitlement";

    private final WebClient webClient;

    public SubscriptionEntitlementGatewayFilterFactory(
            @Qualifier("loadBalancedWebClientBuilder") WebClient.Builder webClientBuilder) {
        super(Config.class);
        this.webClient = webClientBuilder.build();
    }

    public static class Config {
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            String authorization = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
            if (authorization == null || !authorization.startsWith("Bearer ")) {
                return problem(exchange, HttpStatus.UNAUTHORIZED,
                        "Authentication required", "A valid bearer token is required.");
            }

            return webClient.get()
                    .uri(ENTITLEMENT_URL)
                    .header(HttpHeaders.AUTHORIZATION, authorization)
                    .retrieve()
                    .bodyToMono(EntitlementDecision.class)
                    .switchIfEmpty(Mono.error(new IllegalStateException(
                            "The subscription service returned no entitlement decision.")))
                    .timeout(Duration.ofSeconds(3))
                    .flatMap(decision -> decision.active()
                            ? chain.filter(exchange)
                            : problem(exchange, HttpStatus.PAYMENT_REQUIRED,
                            "Active subscription required",
                            decision.reason() == null
                                    ? "This workspace requires an active subscription."
                                    : decision.reason()))
                    .onErrorResume(error -> {
                        log.error("Subscription entitlement check failed for {}: {}",
                                exchange.getRequest().getURI().getPath(), error.getMessage());
                        return problem(exchange, HttpStatus.SERVICE_UNAVAILABLE,
                                "Subscription check unavailable",
                                "The platform could not verify this workspace subscription.");
                    });
        };
    }

    private Mono<Void> problem(ServerWebExchange exchange, HttpStatus status, String title, String detail) {
        var response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);
        String json = "{\"title\":\"" + escape(title) + "\",\"status\":" + status.value()
                + ",\"detail\":\"" + escape(detail) + "\"}";
        var buffer = response.bufferFactory().wrap(json.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }

    private String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private record EntitlementDecision(boolean active, String reason) {
    }
}
