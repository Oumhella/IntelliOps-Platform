package org.example.mcpserver.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.mcpserver.approval.ApprovalService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Gateway-backed OpenAPI adapter. It intentionally resolves the document on
 * every tool request so added operations become available without rebuilding
 * this service. Writes always use the ApprovalService two-step flow.
 */
@Component
public class OpenApiMcpTools {
    private static final Map<String, String> SERVICE_DOCUMENTS = Map.of(
            "user", "/user-service/v3/api-docs",
            "stock", "/stock-service/v3/api-docs",
            "lead", "/lead-service/v3/api-docs",
            "abonnement", "/abonnement-service/v3/api-docs",
            "paiement", "/paiement-service/v3/api-docs",
            "delivery", "/delivery-service/v3/api-docs",
            "notification", "/notification-service/v3/api-docs"
    );
    private static final Set<String> BLOCKED_PATHS = Set.of(
            "/api/v1/users/login", "/api/v1/users/register", "/api/v1/users/setup-admin"
    );

    private final RestClient gatewayClient;
    private final ObjectMapper objectMapper;
    private final ApprovalService approvalService;

    public OpenApiMcpTools(@Qualifier("gatewayClient") RestClient gatewayClient,
                           ObjectMapper objectMapper,
                           ApprovalService approvalService) {
        this.gatewayClient = gatewayClient;
        this.objectMapper = objectMapper;
        this.approvalService = approvalService;
    }

    @Tool(description = "Lists every gateway Swagger operation available to MCP. Use its service and operationId in the read or mutation tools. GET operations are read-only; all other methods require preview and explicit confirmation.")
    public String listerOperationsOpenApi() {
        try {
            List<OperationSummary> operations = new ArrayList<>();
            for (String service : SERVICE_DOCUMENTS.keySet()) {
                for (ApiOperation operation : operationsFor(service)) {
                    operations.add(new OperationSummary(service, operation.operationId(), operation.method().name(),
                            operation.path(), operation.summary(), operation.method() == HttpMethod.GET));
                }
            }
            return objectMapper.writeValueAsString(operations);
        }
        catch (Exception exception) {
            throw unavailable("Unable to load one or more OpenAPI documents", exception);
        }
    }

    @Tool(description = "Executes any documented GET operation through the authenticated API gateway. Only use an operationId returned by listerOperationsOpenApi. Path and query parameter names must exactly match Swagger.")
    public String executerLectureOpenApi(
            @ToolParam(description = "Service: user, stock, lead, abonnement, paiement, delivery, or notification") String service,
            @ToolParam(description = "Swagger operationId returned by listerOperationsOpenApi") String operationId,
            @ToolParam(description = "Path parameters keyed by their Swagger names; use an empty object when none are required") Map<String, String> pathParameters,
            @ToolParam(description = "Query parameters keyed by their Swagger names; use an empty object when none are required") Map<String, String> queryParameters) {
        ApiOperation operation = findOperation(service, operationId);
        if (operation.method() != HttpMethod.GET) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Only GET operations may be executed directly. Prepare non-GET operations first.");
        }
        return gatewayClient.get().uri(resolveUri(operation, pathParameters, queryParameters))
                .retrieve().body(String.class);
    }

    @Tool(description = "PREVIEW ONLY for any documented non-GET Swagger operation. It makes no change. It validates the operation and returns a short-lived approval token with the exact request that would be sent through the gateway.")
    public ApprovalService.ActionPreview preparerMutationOpenApi(
            @ToolParam(description = "Service: user, stock, lead, abonnement, paiement, delivery, or notification") String service,
            @ToolParam(description = "Swagger operationId returned by listerOperationsOpenApi") String operationId,
            @ToolParam(description = "Path parameters keyed by their Swagger names; use an empty object when none are required") Map<String, String> pathParameters,
            @ToolParam(description = "Query parameters keyed by their Swagger names; use an empty object when none are required") Map<String, String> queryParameters,
            @ToolParam(description = "Request body as valid JSON, or an empty string if Swagger declares no body") String requestBodyJson) {
        ApiOperation operation = findOperation(service, operationId);
        if (operation.method() == HttpMethod.GET) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "GET operations must use executerLectureOpenApi.");
        }
        String body = requestBodyJson == null ? "" : requestBodyJson.trim();
        if (!body.isEmpty()) {
            try {
                objectMapper.readTree(body);
            }
            catch (Exception exception) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "requestBodyJson must be valid JSON", exception);
            }
        }
        String uri = resolveUri(operation, pathParameters, queryParameters);
        OpenApiMutation mutation = new OpenApiMutation(operation.method(), uri, body);
        return approvalService.prepare("OPENAPI_MUTATION", mutation,
                "%s %s (%s/%s)".formatted(operation.method(), uri, service, operation.operationId()));
    }

    @Tool(description = "EXECUTION STEP for a previously previewed OpenAPI mutation. Call only after a human explicitly approves the displayed request. The confirmation text must be exactly CONFIRM.")
    public String confirmerMutationOpenApi(
            @ToolParam(description = "Approval token returned by preparerMutationOpenApi") String approvalToken,
            @ToolParam(description = "Must be exactly CONFIRM after human review") String confirmation) {
        OpenApiMutation mutation = approvalService.confirm(approvalToken, "OPENAPI_MUTATION", confirmation,
                OpenApiMutation.class);
        RestClient.RequestBodySpec request = gatewayClient.method(mutation.method()).uri(mutation.uri());
        if (!mutation.requestBodyJson().isEmpty()) {
            return request.contentType(MediaType.APPLICATION_JSON).body(mutation.requestBodyJson())
                    .retrieve().body(String.class);
        }
        return request.retrieve().body(String.class);
    }

    private ApiOperation findOperation(String service, String operationId) {
        return operationsFor(normalizeService(service)).stream()
                .filter(operation -> operation.operationId().equals(operationId))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "OpenAPI operation was not found for service " + service + ": " + operationId));
    }

    private List<ApiOperation> operationsFor(String service) {
        String documentPath = SERVICE_DOCUMENTS.get(normalizeService(service));
        JsonNode document;
        try {
            document = objectMapper.readTree(gatewayClient.get().uri(documentPath).retrieve().body(String.class));
        }
        catch (Exception exception) {
            throw unavailable("OpenAPI document is unavailable for service " + service, exception);
        }
        List<ApiOperation> operations = new ArrayList<>();
        document.path("paths").fields().forEachRemaining(pathEntry -> {
            if (BLOCKED_PATHS.contains(pathEntry.getKey())) {
                return;
            }
            JsonNode pathItem = pathEntry.getValue();
            for (HttpMethod method : List.of(HttpMethod.GET, HttpMethod.POST, HttpMethod.PUT, HttpMethod.PATCH, HttpMethod.DELETE)) {
                JsonNode definition = pathItem.path(method.name().toLowerCase(Locale.ROOT));
                if (!definition.isMissingNode()) {
                    String operationId = definition.path("operationId").asText();
                    if (!operationId.isBlank()) {
                        operations.add(new ApiOperation(operationId, method, pathEntry.getKey(),
                                definition.path("summary").asText(definition.path("description").asText()),
                                parameterNames(pathItem, definition, "path"), parameterNames(pathItem, definition, "query")));
                    }
                }
            }
        });
        return operations;
    }

    private Set<String> parameterNames(JsonNode pathItem, JsonNode operation, String location) {
        Map<String, Boolean> names = new LinkedHashMap<>();
        addParameters(names, pathItem.path("parameters"), location);
        addParameters(names, operation.path("parameters"), location);
        return names.keySet();
    }

    private void addParameters(Map<String, Boolean> names, JsonNode parameters, String location) {
        if (parameters.isArray()) {
            for (JsonNode parameter : parameters) {
                if (location.equals(parameter.path("in").asText())) {
                    names.put(parameter.path("name").asText(), true);
                }
            }
        }
    }

    private String resolveUri(ApiOperation operation, Map<String, String> pathParameters, Map<String, String> queryParameters) {
        Map<String, String> safePathParameters = pathParameters == null ? Map.of() : Map.copyOf(pathParameters);
        Map<String, String> safeQueryParameters = queryParameters == null ? Map.of() : Map.copyOf(queryParameters);
        validateParameters("path", safePathParameters.keySet(), operation.pathParameters());
        validateParameters("query", safeQueryParameters.keySet(), operation.queryParameters());
        UriComponentsBuilder builder = UriComponentsBuilder.fromPath(operation.path());
        safeQueryParameters.forEach(builder::queryParam);
        try {
            return builder.buildAndExpand(safePathParameters).encode().toUriString();
        }
        catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Required path parameters are missing or invalid for " + operation.operationId(), exception);
        }
    }

    private void validateParameters(String type, Collection<String> supplied, Set<String> declared) {
        if (!declared.containsAll(supplied)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Undeclared " + type + " parameter supplied. Allowed names: " + declared);
        }
    }

    private String normalizeService(String service) {
        String normalized = service == null ? "" : service.trim().toLowerCase(Locale.ROOT);
        if (!SERVICE_DOCUMENTS.containsKey(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Unknown service. Allowed values: " + SERVICE_DOCUMENTS.keySet());
        }
        return normalized;
    }

    private ResponseStatusException unavailable(String message, Exception cause) {
        return new ResponseStatusException(HttpStatus.BAD_GATEWAY, message, cause);
    }

    private record ApiOperation(String operationId, HttpMethod method, String path, String summary,
                                Set<String> pathParameters, Set<String> queryParameters) { }
    private record OpenApiMutation(HttpMethod method, String uri, String requestBodyJson) { }
    private record OperationSummary(String service, String operationId, String method, String path, String summary,
                                    boolean readOnly) { }
}
