package org.example.storeintegration.client;

import org.example.storeintegration.dto.IntegrationDtos.Customer;
import org.example.storeintegration.dto.IntegrationDtos.ExternalOrderRequest;
import org.example.storeintegration.config.IntegrationProperties;
import org.example.storeintegration.security.ServiceTokenProvider;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;

import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class CoreOperationsClientTest {

    @Test
    void retriesTransientLeadServiceFailure() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        IntegrationProperties properties = new IntegrationProperties(
                "https://api.example.com", "https://app.example.com", false, 60, "USD",
                new IntegrationProperties.Shopify("client", "secret", "read_orders", "2026-07"),
                "unused");
        ServiceTokenProvider tokenProvider = new ServiceTokenProvider(
                "01234567890123456789012345678901", properties);

        CoreOperationsClient client = new CoreOperationsClient(builder, tokenProvider);
        ReflectionTestUtils.setField(client, "leadUrl", "http://lead-service:8082");
        ReflectionTestUtils.setField(client, "maxAttempts", 2);
        ReflectionTestUtils.setField(client, "retryBackoff", Duration.ZERO);

        String endpoint = "http://lead-service:8082/api/v1/internal/integrations/orders";
        server.expect(once(), requestTo(endpoint)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        server.expect(once(), requestTo(endpoint)).andRespond(withSuccess());

        client.importOrder(7L, new ExternalOrderRequest(
                "SHOPIFY", "order-1", "#1", 3L,
                new Customer("Customer", "customer@example.com", null, "Street", "City"),
                "PAID", "USD", BigDecimal.TEN, List.of()));

        server.verify();
    }
}
