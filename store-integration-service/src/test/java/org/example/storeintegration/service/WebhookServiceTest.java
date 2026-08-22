package org.example.storeintegration.service;

import org.example.storeintegration.dto.IntegrationDtos.Customer;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WebhookServiceTest {

    @Test
    void reportsTheExactMissingShopifyCustomerFields() {
        Customer customer = new Customer(
                "Test Customer",
                null,
                "",
                "Shopify Store Order #1001",
                "N/A");

        assertThat(WebhookService.missingCustomerFields(customer))
                .containsExactly("email or phone", "street address", "city");
    }

    @Test
    void acceptsEmailAndARealDeliveryAddress() {
        Customer customer = new Customer(
                "Test Customer",
                "customer@example.com",
                null,
                "10 Example Street",
                "Casablanca");

        assertThat(WebhookService.missingCustomerFields(customer)).isEmpty();
    }

    @Test
    void reportsEveryRequiredFieldWhenCustomerIsAbsent() {
        assertThat(WebhookService.missingCustomerFields(null))
                .containsExactly("customer name", "email or phone", "street address", "city");
    }
}
