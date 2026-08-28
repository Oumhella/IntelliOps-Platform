package org.example.mcpserver.agent;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.Arrays;
import java.text.Normalizer;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Prevents the model from inventing a mutation intent or target. */
@Component
public class AgentActionIntentGuard {
    private static final Pattern EXPLICIT_WRITE = Pattern.compile(
            "(?iu)(?:\\b(create|convert|adjust|add|remove|refund|reimburse|cancel|assign|start|complete|mark|update|change|delete|send|replenish|return|prepare|confirm|annuler|rembourser|modifier|ajouter|supprimer|assigner|affecter|convertir|preparer)\\b|إنشاء|تعديل|إلغاء|تأكيد|إسناد|حضر)");
    private static final Set<String> GENERIC_OPERATION_WORDS = Set.of(
            "create", "update", "delete", "patch", "post", "put", "api", "operation", "request", "confirm");
    private static final Pattern JSON_STRING_VALUE = Pattern.compile(":\\s*\"([^\"]+)\"");
    private static final Pattern JSON_NUMBER_VALUE = Pattern.compile(":\\s*(-?\\d+(?:\\.\\d+)?)\\s*[,}]");
    private final ThreadLocal<IntentContext> current = new ThreadLocal<>();

    public void begin(String userMessage) {
        String message = userMessage == null ? "" : userMessage.trim();
        current.set(new IntentContext(message, EXPLICIT_WRITE.matcher(normalizeIntent(message)).find()));
    }

    public boolean actionsAllowed() {
        IntentContext context = current.get();
        return context != null && context.explicitWrite();
    }

    public void requireExplicitWrite() {
        if (!actionsAllowed()) {
            throw rejected("No explicit write intent was provided. Ask a question or clearly describe the change you want to prepare.");
        }
    }

    public void requireIdsPresent(Object... identifiers) {
        requireExplicitWrite();
        String message = context().message();
        for (Object identifier : identifiers) {
            if (identifier == null || !containsValue(message, identifier.toString())) {
                throw rejected("Every affected resource ID must be explicitly supplied by the user. Missing or invented targets are not allowed.");
            }
        }
    }

    public void requireGenericMutation(String operationId, Map<String, String> pathParameters,
                                       Map<String, String> queryParameters, String requestBodyJson) {
        requireExplicitWrite();
        String message = context().message();
        Map<String, String> safePath = pathParameters == null ? Map.of() : pathParameters;
        safePath.values().forEach(value -> {
            if (value == null || value.isBlank() || !containsValue(message, value)) {
                throw rejected("The mutation target must appear explicitly in the user's request. Invented path parameters are blocked.");
            }
        });
        Map<String, String> safeQuery = queryParameters == null ? Map.of() : queryParameters;
        safeQuery.values().forEach(value -> requireSuppliedValue(message, value,
                "Invented query parameters are blocked."));
        requireBodyValues(message, requestBodyJson);
        boolean relevant = Arrays.stream(splitOperationId(operationId))
                .filter(word -> word.length() >= 4 && !GENERIC_OPERATION_WORDS.contains(word))
                .anyMatch(word -> message.toLowerCase().contains(word))
                || matchesKnownSynonym(operationId, message);
        if (!relevant) {
            throw rejected("The selected operation does not clearly match the user's requested business action.");
        }
    }

    public void requireGenericMutation(String operationId, Map<String, String> pathParameters) {
        requireGenericMutation(operationId, pathParameters, Map.of(), "");
    }

    public void clear() {
        current.remove();
    }

    private IntentContext context() {
        IntentContext context = current.get();
        if (context == null) throw rejected("No active user request is available for this action preview.");
        return context;
    }

    private boolean containsValue(String message, String value) {
        return Pattern.compile("(?<![A-Za-z0-9])" + Pattern.quote(value) + "(?![A-Za-z0-9])",
                Pattern.CASE_INSENSITIVE).matcher(message).find();
    }

    private String normalizeIntent(String message) {
        return Normalizer.normalize(message, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT);
    }

    private void requireBodyValues(String message, String body) {
        if (body == null || body.isBlank()) return;
        var strings = JSON_STRING_VALUE.matcher(body);
        while (strings.find()) {
            requireSuppliedValue(message, strings.group(1), "Invented request-body values are blocked.");
        }
        var numbers = JSON_NUMBER_VALUE.matcher(body);
        while (numbers.find()) {
            requireSuppliedValue(message, numbers.group(1), "Invented request-body values are blocked.");
        }
    }

    private void requireSuppliedValue(String message, String value, String error) {
        if (value == null || value.isBlank() || !containsValue(message, value)) {
            throw rejected(error);
        }
    }

    private String[] splitOperationId(String operationId) {
        String value = operationId == null ? "" : operationId.replaceAll("([a-z])([A-Z])", "$1 $2");
        return value.toLowerCase().split("[^a-z0-9]+");
    }

    private boolean matchesKnownSynonym(String operationId, String message) {
        String operation = operationId == null ? "" : operationId.toLowerCase();
        String request = message.toLowerCase();
        return (operation.matches(".*(rembours|refund).*") && request.matches(".*\\b(refund|reimburse|rembourser)\\b.*"))
                || (operation.matches(".*(annul|cancel).*") && request.matches(".*\\b(cancel|annuler)\\b.*"))
                || (operation.matches(".*(assign|affect).*") && request.matches(".*\\b(assign|assigner)\\b.*"))
                || (operation.matches(".*(convert).*") && request.matches(".*\\b(convert|convertir)\\b.*"));
    }

    private ResponseStatusException rejected(String reason) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, reason + " No change was made.");
    }

    private record IntentContext(String message, boolean explicitWrite) { }
}
