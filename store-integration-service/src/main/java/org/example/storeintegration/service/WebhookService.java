package org.example.storeintegration.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.storeintegration.client.CoreOperationsClient;
import org.example.storeintegration.config.IntegrationProperties;
import org.example.storeintegration.connector.ShopifyConnector;
import org.example.storeintegration.connector.SignatureVerifier;
import org.example.storeintegration.connector.TokenRefreshedException;
import org.example.storeintegration.connector.WooCommerceConnector;
import org.example.storeintegration.domain.*;
import org.example.storeintegration.dto.IntegrationDtos.*;
import org.example.storeintegration.entity.*;
import org.example.storeintegration.repository.*;
import org.example.storeintegration.security.CredentialCipher;
import org.example.storeintegration.security.CredentialCipher.StoreCredentials;
import org.example.storeintegration.security.ExternalUrlPolicy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class WebhookService {
    private final ObjectMapper objectMapper;
    private final IntegrationProperties properties;
    private final CredentialCipher credentialCipher;
    private final ExternalUrlPolicy urlPolicy;
    private final ShopifyConnector shopifyConnector;
    private final WooCommerceConnector wooCommerceConnector;
    private final CoreOperationsClient coreClient;
    private final StoreConnectionRepository connectionRepository;
    private final ProductMappingRepository mappingRepository;
    private final WebhookEventRepository eventRepository;

    @Transactional
    public WebhookEvent receiveShopify(Long connectionId, byte[] payload, String hmac, String eventId, String topic,
            String shopDomain) {
        StoreConnection connection = publicConnection(connectionId, StorePlatform.SHOPIFY);
        if (!shopifyConnector.validWebhook(payload, hmac))
            throw new SecurityException("Invalid Shopify webhook signature.");
        if (shopDomain == null || !connection.getStoreUrl().equalsIgnoreCase("https://" + shopDomain))
            throw new SecurityException("Shopify webhook shop does not match this connection.");
        return ingest(connection, payload, required(topic, "Shopify webhook topic is required."),
                required(eventId, "Shopify webhook ID is required."), true);
    }

    @Transactional
    public WebhookEvent receiveWooCommerce(Long connectionId, byte[] payload, String signature, String eventId,
            String topic) {
        StoreConnection connection = publicConnection(connectionId, StorePlatform.WOOCOMMERCE);
        var credentials = credentialCipher.decrypt(connection.getEncryptedCredentials());
        if (!wooCommerceConnector.validWebhook(credentials, payload, signature))
            throw new SecurityException("Invalid WooCommerce webhook signature.");
        return ingest(connection, payload, required(topic, "WooCommerce webhook topic is required."),
                required(eventId, "WooCommerce delivery ID is required."), false);
    }

    private WebhookEvent ingest(StoreConnection connection, byte[] payload, String topic, String eventId,
            boolean shopify) {
        if (connection.getStatus() == ConnectionStatus.DISCONNECTED)
            throw new IllegalStateException("This integration is disconnected.");
        WebhookEvent event = eventRepository.findByConnectionIdAndExternalEventId(connection.getId(), eventId)
                .orElse(null);
        if (event != null && event.getStatus() == WebhookEventStatus.PROCESSED)
            return event;
        if (event == null) {
            event = eventRepository.save(WebhookEvent.builder().connection(connection).externalEventId(eventId)
                    .topic(topic).payloadHash(SignatureVerifier.sha256(payload)).status(WebhookEventStatus.RECEIVED)
                    .build());
        }
        try {
            boolean supported = shopify
                    ? List.of("orders/create", "orders/updated", "orders/cancelled")
                            .contains(topic.toLowerCase(Locale.ROOT))
                    : List.of("order.created", "order.updated").contains(topic.toLowerCase(Locale.ROOT));
            if (!supported)
                return actionRequired(event, "Unsupported webhook topic: " + topic);
            JsonNode body = objectMapper.readTree(payload);
            if (shopify) {
                body = enrichShopifyOrder(connection, body);
            }
            NormalizedOrder order = shopify ? shopifyOrder(body) : wooOrder(body);
            boolean createTopic = topic.toLowerCase(Locale.ROOT).endsWith("create")
                    || topic.toLowerCase(Locale.ROOT).endsWith("created");
            if (!createTopic && order.cancelled()) {
                try {
                    coreClient.syncOrderState(connection.getEnterpriseId(), new ExternalOrderStateRequest(
                            connection.getPlatform().name(), order.id(), order.currentPaymentStatus(), true,
                            order.totalAmount(), List.of()));
                    return processed(event, connection);
                } catch (HttpClientErrorException.NotFound missingOrder) {
                    // A create delivery may have been missed; the full update payload can safely
                    // bootstrap it below.
                }
            }
            List<ExternalOrderLine> items = new ArrayList<>();
            List<String> missing = new ArrayList<>();
            for (NormalizedLine line : order.lines()) {
                ProductMapping mapping = mappingRepository
                        .findByConnectionIdAndExternalVariantId(connection.getId(), line.externalVariantId())
                        .orElse(null);
                if (mapping != null) {
                    try {
                        coreClient.verifyProductAndLocation(connection.getEnterpriseId(),
                                mapping.getInternalProductId(), connection.getStockLocationId());
                    } catch (Exception missingProduct) {
                        Long newInternalId = coreClient.importProduct(connection.getEnterpriseId(), line.label(),
                                connection.getPlatform().name() + "-" + line.externalVariantId(), line.unitPrice(),
                                connection.getStockLocationId(), 0);
                        if (newInternalId != null) {
                            mapping.setInternalProductId(newInternalId);
                            mapping = mappingRepository.save(mapping);
                        } else {
                            mapping = null;
                        }
                    }
                }
                if (mapping == null) {
                    Long newInternalId = coreClient.importProduct(connection.getEnterpriseId(), line.label(),
                            connection.getPlatform().name() + "-" + line.externalVariantId(), line.unitPrice(),
                            connection.getStockLocationId(), 0);
                    if (newInternalId != null) {
                        mapping = ProductMapping.builder()
                                .connection(connection)
                                .enterpriseId(connection.getEnterpriseId())
                                .externalProductId(line.externalVariantId())
                                .externalVariantId(line.externalVariantId())
                                .externalName(line.label())
                                .internalProductId(newInternalId)
                                .build();
                        mapping = mappingRepository.save(mapping);
                    }
                }
                if (mapping == null) {
                    missing.add(line.label() + " [" + line.externalVariantId() + "]");
                    continue;
                }
                items.add(new ExternalOrderLine(mapping.getInternalProductId(), line.quantity(), line.unitPrice()));
            }
            if (!missing.isEmpty())
                return actionRequired(event,
                        "Map these external variants before the order can be imported: " + String.join(", ", missing));
            if (items.isEmpty())
                return actionRequired(event, "The external order contains no importable line items.");
            if (!createTopic) {
                try {
                    coreClient.syncOrderState(connection.getEnterpriseId(), new ExternalOrderStateRequest(
                            connection.getPlatform().name(), order.id(), order.currentPaymentStatus(), false,
                            order.totalAmount(), items));
                    return processed(event, connection);
                } catch (HttpClientErrorException.NotFound missingOrder) {
                    // A create delivery may have been missed; the full update payload can safely
                    // bootstrap it below.
                } catch (HttpClientErrorException.Conflict changedOrder) {
                    return actionRequired(event,
                            "The external order's items or total changed. Reconcile it before fulfillment.");
                }
            }
            if (shopify && !hasImportableCustomer(order.customer())) {
                return actionRequired(event, incompleteShopifyCustomerMessage(order.customer()));
            }
            coreClient.importOrder(connection.getEnterpriseId(),
                    new ExternalOrderRequest(connection.getPlatform().name(), order.id(), order.reference(),
                            connection.getStockLocationId(), order.customer(), order.initialPaymentStatus(),
                            order.currency(), order.totalAmount(), items));
            if (!order.initialPaymentStatus().equals(order.currentPaymentStatus()) || order.cancelled()
                    || !createTopic) {
                coreClient.syncOrderState(connection.getEnterpriseId(), new ExternalOrderStateRequest(
                        connection.getPlatform().name(), order.id(), order.currentPaymentStatus(), order.cancelled(),
                        order.totalAmount(), items));
            }
            return processed(event, connection);
        } catch (ActionRequiredException exception) {
            return actionRequired(event, exception.getMessage());
        } catch (RuntimeException exception) {
            event.setStatus(WebhookEventStatus.FAILED);
            event.setErrorMessage(safeMessage(exception));
            event.setProcessedAt(Instant.now());
            eventRepository.save(event);
            connection.setLastError("Order import failed: " + safeMessage(exception));
            connectionRepository.save(connection);
            throw exception;
        } catch (Exception exception) {
            event.setStatus(WebhookEventStatus.FAILED);
            event.setErrorMessage(safeMessage(exception));
            event.setProcessedAt(Instant.now());
            eventRepository.save(event);
            throw new IllegalStateException("Webhook payload could not be processed.", exception);
        }
    }

    private NormalizedOrder shopifyOrder(JsonNode order) {
        String currency = requireCurrency(order.path("currency").asText(null));
        JsonNode customerNode = order.path("customer");
        JsonNode defaultAddr = customerNode.path("default_address");
        JsonNode shippingAddr = order.path("shipping_address");
        JsonNode billingAddr = order.path("billing_address");
        JsonNode address = chooseShopifyAddress(shippingAddr, billingAddr, defaultAddr);

        String orderNum = order.path("name").asText(order.path("order_number").asText(""));

        String name = text(address, "name",
                join(text(address, "first_name", text(shippingAddr, "first_name", text(billingAddr, "first_name", text(customerNode, "first_name", "")))),
                        text(address, "last_name", text(shippingAddr, "last_name", text(billingAddr, "last_name", text(customerNode, "last_name", ""))))));
        if (name.isBlank() || "Shopify Customer".equals(name)) {
            name = join(text(customerNode, "first_name", ""), text(customerNode, "last_name", ""));
        }
        if (name.isBlank()) {
            name = text(address, "company", text(shippingAddr, "company", text(billingAddr, "company", text(defaultAddr, "company", ""))));
        }
        if (name.isBlank()) {
            name = "Shopify Customer " + orderNum;
        }

        String email = text(order, "email",
                text(order, "contact_email",
                        text(customerNode, "email",
                                text(shippingAddr, "email",
                                        text(billingAddr, "email",
                                                text(defaultAddr, "email", null))))));
        if (email != null && email.isBlank())
            email = null;

        String phone = text(address, "phone",
                text(shippingAddr, "phone",
                        text(billingAddr, "phone",
                                text(customerNode, "phone",
                                        text(defaultAddr, "phone",
                                                text(order, "phone", null))))));
        if (phone != null && phone.isBlank())
            phone = null;

        String addrStr = shopifyAddress(shippingAddr);
        if (addrStr.isBlank())
            addrStr = shopifyAddress(billingAddr);
        if (addrStr.isBlank())
            addrStr = shopifyAddress(defaultAddr);
        if (addrStr.isBlank())
            addrStr = shopifyAddress(address);
        if (addrStr.isBlank())
            addrStr = "Shopify Store Order " + orderNum;

        String cityStr = firstNonBlank(
                text(shippingAddr, "city", null),
                text(billingAddr, "city", null),
                text(defaultAddr, "city", null),
                text(address, "city", null),
                text(shippingAddr, "province", null),
                text(billingAddr, "province", null),
                "N/A");

        Customer customer = new Customer(name, email, phone, addrStr, cityStr);
        List<NormalizedLine> lines = new ArrayList<>();
        for (JsonNode line : order.path("line_items")) {
            String variantId = line.path("variant_id").asText("");
            if (variantId.isBlank() || "null".equals(variantId))
                throw new ActionRequiredException("A Shopify custom line has no variant ID and cannot be mapped.");
            lines.add(new NormalizedLine(variantId, line.path("quantity").asInt(), money(line.path("price").asText()),
                    line.path("name").asText("Shopify item")));
        }
        String financial = order.path("financial_status").asText("").toLowerCase(Locale.ROOT);
        String currentPayment = switch (financial) {
            case "paid" -> "PAID";
            case "refunded" -> "REFUNDED";
            case "partially_refunded" -> "PARTIALLY_REFUNDED";
            default -> isShopifyCod(order) ? "AWAITING_COLLECTION" : "UNPAID";
        };
        String initialPayment = currentPayment.endsWith("REFUNDED") ? "PAID" : currentPayment;
        boolean cancelled = !order.path("cancelled_at").isNull() && !order.path("cancelled_at").asText("").isBlank();
        BigDecimal total = money(order.path("current_total_price").asText(order.path("total_price").asText()));
        return new NormalizedOrder(order.path("id").asText(),
                order.path("name").asText(order.path("order_number").asText()),
                customer, initialPayment, currentPayment, cancelled, currency, total, lines);
    }

    private NormalizedOrder wooOrder(JsonNode order) {
        String currency = requireCurrency(order.path("currency").asText(null));
        JsonNode shipping = order.path("shipping");
        JsonNode billing = order.path("billing");
        JsonNode address = shipping.isObject() && !text(shipping, "address_1", "").isBlank() ? shipping : billing;
        Customer customer = new Customer(join(text(address, "first_name", ""), text(address, "last_name", "")),
                text(billing, "email", null), text(billing, "phone", null), address(address),
                text(address, "city", ""));
        List<NormalizedLine> lines = new ArrayList<>();
        for (JsonNode line : order.path("line_items")) {
            String variant = line.path("variation_id").asLong(0) > 0 ? line.path("variation_id").asText()
                    : line.path("product_id").asText();
            int quantity = line.path("quantity").asInt();
            BigDecimal unit = line.hasNonNull("price") ? line.path("price").decimalValue()
                    : money(line.path("total").asText()).divide(BigDecimal.valueOf(Math.max(1, quantity)), 2,
                            RoundingMode.HALF_UP);
            lines.add(new NormalizedLine(variant, quantity, unit, line.path("name").asText("WooCommerce item")));
        }
        String status = order.path("status").asText("").toLowerCase(Locale.ROOT);
        String method = order.path("payment_method").asText("").toLowerCase(Locale.ROOT);
        String currentPayment = "refunded".equals(status) ? "REFUNDED"
                : ("processing".equals(status) || "completed".equals(status)) ? "PAID"
                        : "cod".equals(method) ? "AWAITING_COLLECTION" : "UNPAID";
        String initialPayment = "REFUNDED".equals(currentPayment) ? "PAID" : currentPayment;
        boolean cancelled = "cancelled".equals(status) || "failed".equals(status) || "refunded".equals(status);
        return new NormalizedOrder(order.path("id").asText(), order.path("number").asText(order.path("id").asText()),
                customer, initialPayment, currentPayment, cancelled, currency, money(order.path("total").asText()),
                lines);
    }

    private JsonNode enrichShopifyOrder(StoreConnection connection, JsonNode webhookOrder) {
        String orderId = webhookOrder.path("id").asText(null);
        if (orderId == null || orderId.isBlank()) {
            return webhookOrder;
        }
        URI store = urlPolicy.requireShopifyStore(connection.getStoreUrl());
        StoreCredentials credentials = credentialCipher.decrypt(connection.getEncryptedCredentials());
        try {
            JsonNode fullOrder = shopifyConnector.fetchOrder(store, credentials, orderId);
            log.debug("enrichShopifyOrder: loaded Admin API order {} for connection {}", orderId, connection.getId());
            return fullOrder;
        } catch (TokenRefreshedException tre) {
            var refreshed = tre.refreshed();
            StoreCredentials updated = StoreCredentials.shopify(refreshed.accessToken(), refreshed.refreshToken(),
                    refreshed.expiresIn() == null ? null
                            : Instant.now().plusSeconds(refreshed.expiresIn()).getEpochSecond());
            connection.setEncryptedCredentials(credentialCipher.encrypt(updated));
            connectionRepository.save(connection);
            return shopifyConnector.fetchOrder(store, updated, orderId);
        } catch (Exception exception) {
            log.warn("enrichShopifyOrder: Admin API fetch failed for order {} ({}), using webhook payload",
                    orderId, exception.getMessage());
            return webhookOrder;
        }
    }

    private boolean hasImportableCustomer(Customer customer) {
        return missingCustomerFields(customer).isEmpty();
    }

    static List<String> missingCustomerFields(Customer customer) {
        List<String> missing = new ArrayList<>();
        if (customer == null) {
            return List.of("customer name", "email or phone", "street address", "city");
        }
        boolean hasName = customer.fullName() != null && !customer.fullName().isBlank();
        boolean hasContact = (customer.email() != null && !customer.email().isBlank())
                || (customer.phone() != null && !customer.phone().isBlank());
        boolean hasCity = customer.city() != null && !customer.city().isBlank()
                && !"N/A".equalsIgnoreCase(customer.city());
        boolean hasAddress = customer.address() != null && !customer.address().isBlank()
                && !customer.address().equalsIgnoreCase(customer.city())
                && !customer.address().startsWith("Shopify Store Order");
        if (!hasName)
            missing.add("customer name");
        if (!hasContact)
            missing.add("email or phone");
        if (!hasAddress)
            missing.add("street address");
        if (!hasCity)
            missing.add("city");
        return missing;
    }

    private String incompleteShopifyCustomerMessage(Customer customer) {
        String missing = String.join(", ", missingCustomerFields(customer));
        return "Shopify order cannot be imported because required customer data is missing: " + missing + ". "
                + "If these fields are present on the order in Shopify, verify Protected Customer Data access "
                + "and reconnect the store before retrying this webhook.";
    }

    private boolean isShopifyCod(JsonNode order) {
        for (JsonNode gateway : order.path("payment_gateway_names"))
            if (gateway.asText("").toLowerCase(Locale.ROOT).contains("cash"))
                return true;
        return false;
    }

    private JsonNode chooseShopifyAddress(JsonNode shipping, JsonNode billing, JsonNode defaultAddr) {
        if (isMeaningfulShopifyAddress(shipping))
            return shipping;
        if (isMeaningfulShopifyAddress(billing))
            return billing;
        if (isMeaningfulShopifyAddress(defaultAddr))
            return defaultAddr;
        if (shipping.isObject() && shipping.size() > 0)
            return shipping;
        if (billing.isObject() && billing.size() > 0)
            return billing;
        return defaultAddr;
    }

    private boolean isMeaningfulShopifyAddress(JsonNode address) {
        if (address == null || address.isMissingNode() || !address.isObject())
            return false;
        return !text(address, "address1", "").isBlank()
                || !text(address, "address_1", "").isBlank()
                || !text(address, "city", "").isBlank()
                || !text(address, "zip", "").isBlank()
                || !text(address, "province", "").isBlank()
                || !text(address, "phone", "").isBlank()
                || !text(address, "company", "").isBlank();
    }

    private WebhookEvent actionRequired(WebhookEvent event, String message) {
        event.setStatus(WebhookEventStatus.ACTION_REQUIRED);
        event.setErrorMessage(message);
        event.setProcessedAt(Instant.now());
        return eventRepository.save(event);
    }

    private WebhookEvent processed(WebhookEvent event, StoreConnection connection) {
        event.setStatus(WebhookEventStatus.PROCESSED);
        event.setErrorMessage(null);
        event.setProcessedAt(Instant.now());
        connection.setLastSyncAt(Instant.now());
        connection.setLastError(null);
        connectionRepository.save(connection);
        return eventRepository.save(event);
    }

    private StoreConnection publicConnection(Long id, StorePlatform platform) {
        StoreConnection c = connectionRepository.findByIdForWebhook(id)
                .orElseThrow(() -> new EntityNotFoundException("Store connection not found."));
        if (c.getPlatform() != platform)
            throw new SecurityException("Webhook platform mismatch.");
        return c;
    }

    private String shopifyAddress(JsonNode node) {
        if (node == null || node.isMissingNode() || !node.isObject())
            return "";
        String a1 = text(node, "address1", text(node, "address_1", ""));
        String a2 = text(node, "address2", text(node, "address_2", ""));
        String zip = text(node, "zip", "");
        String city = text(node, "city", "");
        String company = text(node, "company", "");
        String province = text(node, "province", "");
        String country = text(node, "country_name", text(node, "country", ""));

        List<String> parts = new ArrayList<>();
        String street = join(a1, a2);
        if (!street.isBlank()) parts.add(street);
        if (!company.isBlank()) parts.add(company);
        String location = join(zip, city);
        if (!location.isBlank()) parts.add(location);
        if (!province.isBlank() && !province.equalsIgnoreCase(city)) parts.add(province);
        if (!country.isBlank() && !country.equalsIgnoreCase(city)) parts.add(country);

        return String.join(", ", parts).trim();
    }

    private String address(JsonNode node) {
        return shopifyAddress(node);
    }

    private String join(String first, String second) {
        return (valueOr(first, "") + " " + valueOr(second, "")).trim();
    }

    private String text(JsonNode node, String field, String fallback) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return fallback;
        }
        String value = node.path(field).asText(null);
        return value == null || value.isBlank() ? fallback : value;
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private String valueOr(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String required(String value, String message) {
        if (value == null || value.isBlank())
            throw new IllegalArgumentException(message);
        return value.trim();
    }

    private String requireCurrency(String value) {
        String currency = required(value, "External order currency is required.").toUpperCase(Locale.ROOT);
        String configured = properties.orderCurrency() != null ? properties.orderCurrency().toUpperCase(Locale.ROOT)
                : "USD,MAD,EUR";
        java.util.Set<String> allowed = java.util.Set.of(configured.split("\\s*,\\s*"));
        if (!allowed.contains(currency) && !configured.equals("*")) {
            throw new ActionRequiredException("External order currency " + currency
                    + " does not match workspace accepted currencies (" + configured + ").");
        }
        return currency;
    }

    private BigDecimal money(String value) {
        try {
            BigDecimal amount = new BigDecimal(value);
            if (amount.signum() < 0)
                throw new NumberFormatException();
            return amount;
        } catch (RuntimeException exception) {
            throw new ActionRequiredException("An external line item has an invalid price.");
        }
    }

    private String safeMessage(Exception exception) {
        String value = exception.getMessage();
        return value == null ? exception.getClass().getSimpleName()
                : value.substring(0, Math.min(1000, value.length()));
    }

    private record NormalizedOrder(String id, String reference, Customer customer, String initialPaymentStatus,
            String currentPaymentStatus, boolean cancelled, String currency,
            BigDecimal totalAmount, List<NormalizedLine> lines) {
    }

    private record NormalizedLine(String externalVariantId, int quantity, BigDecimal unitPrice, String label) {
        NormalizedLine {
            if (quantity <= 0)
                throw new ActionRequiredException("An external line item has no positive quantity.");
        }
    }

    private static class ActionRequiredException extends RuntimeException {
        ActionRequiredException(String message) {
            super(message);
        }
    }
}
