package org.example.gateway_service.config;

import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/** Blocks order-owned inventory lifecycle operations from public gateway use. */
@Component
public class InternalStockOperationsGatewayFilterFactory extends
        AbstractGatewayFilterFactory<InternalStockOperationsGatewayFilterFactory.Config> {

    private static final Pattern INTERNAL_OPERATION = Pattern.compile(
            "^/api/v1/inventaires/boutiques/[^/]+/produits/[^/]+/(reserver|liberer|consommer)/?$");

    public InternalStockOperationsGatewayFilterFactory() {
        super(Config.class);
    }

    public static class Config {
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            String path = exchange.getRequest().getURI().getPath();
            if (INTERNAL_OPERATION.matcher(path).matches()) {
                exchange.getResponse().setStatusCode(HttpStatus.NOT_FOUND);
                return exchange.getResponse().setComplete();
            }
            return chain.filter(exchange);
        };
    }
}
