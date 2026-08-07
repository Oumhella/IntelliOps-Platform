package org.example.gateway_service.config;

import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/** Prevents clients from writing service-owned order projection fields. */
@Component
public class InternalOrderOperationsGatewayFilterFactory extends
        AbstractGatewayFilterFactory<InternalOrderOperationsGatewayFilterFactory.Config> {

    private static final Pattern INTERNAL_OPERATION = Pattern.compile(
            "^/api/v1/commandes/[^/]+/(payment-status|fulfillment-status)/?$");

    public InternalOrderOperationsGatewayFilterFactory() {
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
