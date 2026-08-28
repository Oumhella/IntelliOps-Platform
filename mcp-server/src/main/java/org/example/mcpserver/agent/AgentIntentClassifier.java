package org.example.mcpserver.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Optional;

/**
 * Uses the model only for multilingual semantic understanding. It cannot choose
 * URLs or execute tools; its constrained output is validated before routing.
 */
@Component
public class AgentIntentClassifier {
    private static final Logger log = LoggerFactory.getLogger(AgentIntentClassifier.class);
    private static final String CLASSIFIER_PROMPT = """
            Classify the user's ERP request, regardless of the language or wording.
            Return one JSON object only, without markdown or explanation:
            {
              "intent": "ONE_ALLOWED_VALUE",
              "resourceId": null,
              "productId": null,
              "locationId": null,
              "canonicalQuestion": null,
              "confidence": 0.0
            }

            Allowed intent values:
            GREETING, CAPABILITIES, LIST_PRODUCTS, LIST_LEADS, GET_LEAD,
            GET_INVENTORY, ANALYTICS, LIST_ORDERS, GET_ORDER,
            LIST_DELIVERIES, GET_DELIVERY, LIST_PAYMENTS, GET_PAYMENT,
            LIST_NOTIFICATIONS, GET_NOTIFICATION, CURRENT_SUBSCRIPTION, UNSUPPORTED.

            Rules:
            - Understand French, Arabic, Darija, English and mixed-language requests.
            - Never create an ID. Copy an ID only when its number is explicit in the user message.
            - Use GET_* only for one explicitly identified resource; otherwise use LIST_*.
            - For ANALYTICS, translate the complete business question into concise English in
              canonicalQuestion, preserving every requested metric. Do not add a metric.
            - Classification never grants permission and never executes an operation.

            User message:
            """;

    private final ChatClient classifier;
    private final ObjectMapper objectMapper;

    public AgentIntentClassifier(ObjectProvider<ChatModel> modelProvider,
                                 ObjectProvider<ObjectMapper> objectMapperProvider) {
        ChatModel model = modelProvider.getIfAvailable();
        this.classifier = model == null ? null : ChatClient.builder(model).build();
        ObjectMapper mapper = objectMapperProvider.getIfAvailable();
        this.objectMapper = mapper == null ? new ObjectMapper() : mapper;
    }

    public Optional<ClassifiedIntent> classify(String message) {
        if (classifier == null || message == null || message.isBlank()) {
            return Optional.empty();
        }
        try {
            String response = classifier.prompt().user(CLASSIFIER_PROMPT + message).call().content();
            JsonNode json = extractJson(response);
            Intent intent = Intent.valueOf(json.path("intent").asText("UNSUPPORTED").toUpperCase(Locale.ROOT));
            double confidence = json.path("confidence").asDouble(0.0);
            Long resourceId = nullableLong(json, "resourceId");
            Long productId = nullableLong(json, "productId");
            Long locationId = nullableLong(json, "locationId");
            String canonicalQuestion = nullableText(json, "canonicalQuestion");

            if (confidence < 0.60 || !idsAreGrounded(message, resourceId, productId, locationId)) {
                return Optional.empty();
            }
            return Optional.of(new ClassifiedIntent(
                    intent, resourceId, productId, locationId, canonicalQuestion, confidence));
        } catch (Exception exception) {
            log.warn("The semantic intent classifier returned an unusable response: {}", exception.getMessage());
            return Optional.empty();
        }
    }

    private JsonNode extractJson(String response) throws Exception {
        if (response == null) throw new IllegalArgumentException("Empty classifier response");
        int start = response.indexOf('{');
        int end = response.lastIndexOf('}');
        if (start < 0 || end <= start) throw new IllegalArgumentException("Classifier response is not JSON");
        return objectMapper.readTree(response.substring(start, end + 1));
    }

    private boolean idsAreGrounded(String message, Long... ids) {
        for (Long id : ids) {
            if (id != null && !message.matches("(?s).*\\b" + id + "\\b.*")) return false;
        }
        return true;
    }

    private Long nullableLong(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() || !value.canConvertToLong() ? null : value.asLong();
    }

    private String nullableText(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() || !value.isTextual() || value.asText().isBlank()
                ? null : value.asText();
    }

    public enum Intent {
        GREETING, CAPABILITIES, LIST_PRODUCTS, LIST_LEADS, GET_LEAD,
        GET_INVENTORY, ANALYTICS, LIST_ORDERS, GET_ORDER,
        LIST_DELIVERIES, GET_DELIVERY, LIST_PAYMENTS, GET_PAYMENT,
        LIST_NOTIFICATIONS, GET_NOTIFICATION, CURRENT_SUBSCRIPTION, UNSUPPORTED
    }

    public record ClassifiedIntent(Intent intent, Long resourceId, Long productId,
                                   Long locationId, String canonicalQuestion, double confidence) { }
}
