package org.example.storeintegration.connector;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.example.storeintegration.dto.IntegrationDtos.ExternalProduct;
import org.example.storeintegration.security.CredentialCipher.StoreCredentials;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class WooCommerceConnectorTest {
    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void listsVariationDetailsForVariableProducts() throws IOException {
        AtomicBoolean variationsRequested = new AtomicBoolean(false);
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/wp-json/wc/v3/products", exchange -> {
            assertBasicAuth(exchange);
            if (exchange.getRequestURI().getPath().endsWith("/products")) {
                respond(exchange, """
                        [
                          {"id":42,"name":"Sneaker","sku":"SNK","variations":[901,902]},
                          {"id":77,"name":"Sticker","sku":"STICK","price":"3.50","stock_quantity":12,"variations":[]}
                        ]
                        """);
                return;
            }
            if (exchange.getRequestURI().getPath().endsWith("/products/42/variations")) {
                variationsRequested.set(true);
                respond(exchange, """
                        [
                          {"id":901,"sku":"SNK-RED-42","price":"89.99","stock_quantity":7,"attributes":[{"name":"Color","option":"Red"},{"name":"Size","option":"42"}]},
                          {"id":902,"sku":"","price":"95.00","stock_quantity":0,"attributes":[]}
                        ]
                        """);
                return;
            }
            exchange.sendResponseHeaders(404, -1);
        });
        server.setExecutor(Executors.newSingleThreadExecutor());
        server.start();

        WooCommerceConnector connector = new WooCommerceConnector(RestClient.builder());
        URI store = URI.create("http://127.0.0.1:" + server.getAddress().getPort());

        List<ExternalProduct> products = connector.listProducts(store,
                StoreCredentials.woocommerce("ck_test", "cs_test", "whsec"));

        assertThat(variationsRequested).isTrue();
        assertThat(products)
                .extracting(ExternalProduct::productId, ExternalProduct::variantId,
                        ExternalProduct::sku, ExternalProduct::name,
                        ExternalProduct::salePrice, ExternalProduct::availableQuantity)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("42", "901", "SNK-RED-42", "Sneaker - Red / 42",
                                new java.math.BigDecimal("89.99"), 7),
                        org.assertj.core.groups.Tuple.tuple("42", "902", "", "Sneaker - variation 902",
                                new java.math.BigDecimal("95.00"), 0),
                        org.assertj.core.groups.Tuple.tuple("77", "77", "STICK", "Sticker",
                                new java.math.BigDecimal("3.50"), 12));
    }

    private void assertBasicAuth(HttpExchange exchange) {
        String expected = "Basic " + Base64.getEncoder()
                .encodeToString("ck_test:cs_test".getBytes(StandardCharsets.UTF_8));
        assertThat(exchange.getRequestHeaders().getFirst("Authorization")).isEqualTo(expected);
    }

    private void respond(HttpExchange exchange, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
