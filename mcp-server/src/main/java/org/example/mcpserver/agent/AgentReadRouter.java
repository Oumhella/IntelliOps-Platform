package org.example.mcpserver.agent;

import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Routes frequent read requests without asking an LLM to select tools or invent
 * parameters. Authorization and tenant scoping remain enforced by domain APIs.
 */
@Component
public class AgentReadRouter {
    private static final Pattern LEAD_ID = Pattern.compile("\\blead\\s*(?:id\\s*)?[#:]?\\s*(\\d+)\\b");
    private static final Pattern PRODUCT_ID = Pattern.compile("\\bproduct\\s*(?:id\\s*)?[#:]?\\s*(\\d+)\\b");
    private static final Pattern LOCATION_ID = Pattern.compile("\\b(?:store|location)\\s*(?:id\\s*)?[#:]?\\s*(\\d+)\\b");
    private static final Pattern ORDER_ID = Pattern.compile("\\border\\s*(?:id\\s*)?[#:]?\\s*(\\d+)\\b");
    private static final Pattern DELIVERY_ID = Pattern.compile("\\bdelivery\\s*(?:id\\s*)?[#:]?\\s*(\\d+)\\b");
    private static final Pattern PAYMENT_ID = Pattern.compile("\\b(?:payment|transaction)\\s*(?:id\\s*)?[#:]?\\s*(\\d+)\\b");
    private static final Pattern NOTIFICATION_ID = Pattern.compile("\\bnotification\\s*(?:id\\s*)?[#:]?\\s*(\\d+)\\b");

    private final ReadOnlyAgentTools tools;
    private final PlatformReadTools platformTools;

    public AgentReadRouter(ReadOnlyAgentTools tools, PlatformReadTools platformTools) {
        this.tools = tools;
        this.platformTools = platformTools;
    }

    public Optional<RoutedRead> route(String message) {
        String normalized = normalize(message);
        if (isGreeting(normalized)) {
            return Optional.of(RoutedRead.direct("Hi! I can inspect live ERP data, analyze business performance, or prepare a controlled operation for your approval. What would you like to work on?"));
        }
        if (isCapabilityQuestion(normalized)) {
            return Optional.of(RoutedRead.direct(capabilityAnswer()));
        }
        if (isInventoryQuestion(normalized)) {
            Long productId = extractId(PRODUCT_ID, normalized);
            Long locationId = extractId(LOCATION_ID, normalized);
            if (productId == null || locationId == null) {
                return Optional.of(RoutedRead.direct(
                        "Please provide both the product ID and the store/location ID so I can retrieve the authoritative inventory record."));
            }
            return Optional.of(RoutedRead.backend(tools.getInventory(locationId, productId)));
        }
        if (isProductListQuestion(normalized)) {
            return Optional.of(RoutedRead.backend(tools.listProducts()));
        }
        if (isLeadQuestion(normalized)) {
            Long leadId = extractId(LEAD_ID, normalized);
            return Optional.of(RoutedRead.backend(
                    leadId == null ? tools.listVisibleLeads() : tools.getLead(leadId)));
        }
        if (isAnalyticsQuestion(normalized)) {
            if (mentionsRevenue(normalized) && mentionsOrdersByStatus(normalized)) {
                String revenue = tools.askBusinessQuestion("What is the current paid revenue?");
                String orders = tools.askBusinessQuestion("How many orders are there by status?");
                return Optional.of(RoutedRead.backend(
                        "VERIFIED REVENUE RESULT:\n" + revenue + "\n\nVERIFIED ORDERS-BY-STATUS RESULT:\n" + orders));
            }
            return Optional.of(RoutedRead.backend(tools.askBusinessQuestion(message)));
        }
        if (normalized.matches(".*\\borders?\\b.*")) {
            Long id = extractId(ORDER_ID, normalized);
            return Optional.of(RoutedRead.backend(id == null ? platformTools.listOrders() : platformTools.getOrder(id)));
        }
        if (normalized.matches(".*\\b(deliveries|delivery|shipments?)\\b.*")) {
            Long id = extractId(DELIVERY_ID, normalized);
            return Optional.of(RoutedRead.backend(id == null ? platformTools.listDeliveries() : platformTools.getDelivery(id)));
        }
        if (normalized.matches(".*\\b(payments?|transactions?)\\b.*")) {
            Long id = extractId(PAYMENT_ID, normalized);
            return Optional.of(RoutedRead.backend(id == null ? platformTools.listPayments() : platformTools.getPayment(id)));
        }
        if (normalized.matches(".*\\bnotifications?\\b.*")) {
            Long id = extractId(NOTIFICATION_ID, normalized);
            return Optional.of(RoutedRead.backend(id == null ? platformTools.listNotifications() : platformTools.getNotification(id)));
        }
        if (normalized.matches(".*\\b(plan|subscription|entitlement|abonnement)\\b.*")) {
            return Optional.of(RoutedRead.backend(platformTools.currentSubscription()));
        }
        return Optional.empty();
    }

    public Optional<RoutedRead> route(String originalMessage,
                                      AgentIntentClassifier.ClassifiedIntent classified) {
        if (classified == null || classified.intent() == AgentIntentClassifier.Intent.UNSUPPORTED) {
            return Optional.empty();
        }
        return Optional.of(switch (classified.intent()) {
            case GREETING -> RoutedRead.direct("Hi! I can inspect live ERP data, analyze business performance, or prepare a controlled operation for your approval. What would you like to work on?");
            case CAPABILITIES -> RoutedRead.direct(capabilityAnswer());
            case LIST_PRODUCTS -> RoutedRead.backend(tools.listProducts());
            case LIST_LEADS -> RoutedRead.backend(tools.listVisibleLeads());
            case GET_LEAD -> classified.resourceId() == null
                    ? RoutedRead.direct("Please provide the lead ID you want to inspect.")
                    : RoutedRead.backend(tools.getLead(classified.resourceId()));
            case GET_INVENTORY -> classified.productId() == null || classified.locationId() == null
                    ? RoutedRead.direct("Please provide both the product ID and the store/location ID so I can retrieve the authoritative inventory record.")
                    : RoutedRead.backend(tools.getInventory(classified.locationId(), classified.productId()));
            case ANALYTICS -> routeAnalytics(classified.canonicalQuestion() == null
                    ? originalMessage : classified.canonicalQuestion());
            case LIST_ORDERS -> RoutedRead.backend(platformTools.listOrders());
            case GET_ORDER -> resourceOrClarification(classified.resourceId(), "order", platformTools::getOrder);
            case LIST_DELIVERIES -> RoutedRead.backend(platformTools.listDeliveries());
            case GET_DELIVERY -> resourceOrClarification(classified.resourceId(), "delivery", platformTools::getDelivery);
            case LIST_PAYMENTS -> RoutedRead.backend(platformTools.listPayments());
            case GET_PAYMENT -> resourceOrClarification(classified.resourceId(), "payment", platformTools::getPayment);
            case LIST_NOTIFICATIONS -> RoutedRead.backend(platformTools.listNotifications());
            case GET_NOTIFICATION -> resourceOrClarification(classified.resourceId(), "notification", platformTools::getNotification);
            case CURRENT_SUBSCRIPTION -> RoutedRead.backend(platformTools.currentSubscription());
            case UNSUPPORTED -> throw new IllegalStateException("Unsupported intent was already rejected");
        });
    }

    private RoutedRead routeAnalytics(String question) {
        String normalized = normalize(question);
        if (mentionsRevenue(normalized) && mentionsOrdersByStatus(normalized)) {
            String revenue = tools.askBusinessQuestion("What is the current paid revenue?");
            String orders = tools.askBusinessQuestion("How many orders are there by status?");
            return RoutedRead.backend("VERIFIED REVENUE RESULT:\n" + revenue
                    + "\n\nVERIFIED ORDERS-BY-STATUS RESULT:\n" + orders);
        }
        return RoutedRead.backend(tools.askBusinessQuestion(question));
    }

    private RoutedRead resourceOrClarification(Long id, String resource,
                                                java.util.function.LongFunction<String> lookup) {
        return id == null
                ? RoutedRead.direct("Please provide the " + resource + " ID you want to inspect.")
                : RoutedRead.backend(lookup.apply(id));
    }

    static boolean isCapabilityQuestion(String value) {
        return value.matches(".*\\b(what|which|show|tell)\\b.*\\b(can|could)\\b.*\\b(do|help)\\b.*")
                || value.matches(".*\\bwhat else\\b.*")
                || value.matches(".*\\b(capabilities|available actions|help menu)\\b.*");
    }

    private static boolean isGreeting(String value) {
        return value.matches("^(hi+|he+y+|hello+|hey+|bonjour|salut)[!.? ]*$");
    }

    private static boolean isProductListQuestion(String value) {
        return value.matches(".*\\b(products?|catalog|articles?)\\b.*")
                && value.matches(".*\\b(show|list|available|have|catalog|all|voir|liste)\\b.*");
    }

    private static boolean isLeadQuestion(String value) {
        return value.matches(".*\\bleads?\\b.*")
                && value.matches(".*\\b(show|list|find|get|assigned|queue|lead|voir|liste)\\b.*");
    }

    private static boolean isInventoryQuestion(String value) {
        return value.matches(".*\\b(inventory|stock|availability|available quantity)\\b.*");
    }

    private static boolean isAnalyticsQuestion(String value) {
        return value.matches(".*\\b(revenue|sales|turnover|metric|trend|ranking|orders? by status|stock value|chiffre d.?affaires|report|analytics)\\b.*");
    }

    private static boolean mentionsRevenue(String value) {
        return value.matches(".*\\b(revenue|sales|turnover|chiffre d.?affaires)\\b.*");
    }

    private static boolean mentionsOrdersByStatus(String value) {
        return value.matches(".*\\borders?\\b.*") && value.matches(".*\\bstatus|statuses|state|states\\b.*");
    }

    private static Long extractId(Pattern pattern, String value) {
        Matcher matcher = pattern.matcher(value);
        return matcher.find() ? Long.valueOf(matcher.group(1)) : null;
    }

    private static String normalize(String message) {
        return message == null ? "" : message.trim().toLowerCase(Locale.ROOT);
    }

    private static String capabilityAnswer() {
        return """
                **I can help in three controlled modes:**

                - **Investigate:** retrieve live products, inventory, your authorized lead queue, orders, deliveries, payments, notifications, and subscriptions.
                - **Analyze:** answer BI questions about revenue, order status, stock, rankings, trends, charts, and reports.
                - **Prepare operations:** build role-authorized changes such as stock adjustments, lead conversion, order workflow, delivery actions, or payment operations.

                Read requests use authenticated, tenant-scoped ERP endpoints. For a write, I require exact targets and values and create an approval card; only you can confirm execution.
                """;
    }

    public record RoutedRead(String directAnswer, String backendResult) {
        static RoutedRead direct(String answer) {
            return new RoutedRead(answer, null);
        }

        static RoutedRead backend(String result) {
            return new RoutedRead(null, result);
        }

        public boolean isDirect() {
            return directAnswer != null;
        }
    }
}
