package org.example.gateway_service.config;

import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/** Keeps amount-bearing and service-to-service payment endpoints off the public API. */
@Component
public class InternalPaymentOperationsGatewayFilterFactory extends
        AbstractGatewayFilterFactory<InternalPaymentOperationsGatewayFilterFactory.Config> {

    private static final Pattern INTERNAL_OPERATION = Pattern.compile(
            "^/api/v1/payments/(prepare|initier|[^/]+/consume|orders/[^/]+/collect-cod)/?$");

    public InternalPaymentOperationsGatewayFilterFactory() {
        super(Config.class);
    }

    public static class Config {
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            if (INTERNAL_OPERATION.matcher(exchange.getRequest().getURI().getPath()).matches()) {
                exchange.getResponse().setStatusCode(HttpStatus.NOT_FOUND);
                return exchange.getResponse().setComplete();
            }
            return chain.filter(exchange);
        };
    }
}
