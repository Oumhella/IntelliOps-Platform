package org.example.mcpserver.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.mcpserver.approval.ApprovalService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Service
public class AgentChatService implements AgentChat {
    private static final Logger log = LoggerFactory.getLogger(AgentChatService.class);
    private static final String SYSTEM_PROMPT = """
            You are the ERP IntelliOps operations assistant. Help users understand current ERP
            data and safely operate inventory, CRM, orders, payments, deliveries, and other
            documented workflows using the supplied tools.

            TOOL ROUTING:
            - Products / product list / available products / catalog -> ALWAYS call listProducts.
            - Inventory / store stock -> call getInventory.
            - CRM leads -> call getLead or listAgentLeads.
            - Business trends, metrics, rankings, revenue -> call askBusinessQuestion.
            - Other read operations -> call listOpenApiReadOperations then executeOpenApiRead.
            - Stock adjustment -> inspect inventory, then call previewStockAdjustment.
            - Qualified lead conversion -> inspect the lead and products, then call previewLeadConversion.
            - Other business changes -> call listAvailableOperations, select the exact non-GET operation,
              then call previewOpenApiMutation.

            RESPONSE RULES:
            1. For data questions, ALWAYS call a tool to get real ERP data. For capability questions,
               explain available capabilities directly without calling any business tool.
            2. Present all information in clean, professional natural language markdown (bullet points, bold headings, or markdown tables).
            3. NEVER output raw JSON, function call parameter JSON like {"name": "..."}, or tool signatures to the user.
            4. You may PREVIEW a write operation, but you can never confirm or execute it. After a preview,
               explain the impact and tell the user to use the approval card. Never claim that a preview changed data.
            5. Respect the authenticated role: CSM handles assigned leads and customer/order handoff;
               LOGISTIC handles stock, preparation and assignment; LIVREUR handles only assigned delivery
               execution; ADMIN handles workspace administration. Domain APIs enforce the final permission.
            6. NEVER invent an ID, amount, customer, order, payment, product, location, or status. If a write
               request lacks an exact target or required value, ask a concise clarification question.
            """;

    private final ChatClient chatClient;
    private final ToolCallbackProvider readOnlyToolProvider;
    private final ToolCallbackProvider operationalToolProvider;
    private final ReadOnlyAgentTools readOnlyAgentTools;
    private final ActionPreviewAgentTools actionPreviewAgentTools;
    private final ApprovalService approvalService;
    private final AgentActionIntentGuard intentGuard;
    private final ObjectMapper objectMapper;

    public AgentChatService(ObjectProvider<ChatModel> chatModelProvider,
                            ReadOnlyAgentTools readOnlyAgentTools,
                            ActionPreviewAgentTools actionPreviewAgentTools,
                            ApprovalService approvalService,
                            AgentActionIntentGuard intentGuard,
                            ObjectProvider<ObjectMapper> objectMapperProvider) {
        ChatModel chatModel = chatModelProvider.getIfAvailable();
        this.chatClient = chatModel == null ? null : ChatClient.builder(chatModel)
                .defaultSystem(SYSTEM_PROMPT)
                .build();
        this.readOnlyToolProvider = MethodToolCallbackProvider.builder()
                .toolObjects(readOnlyAgentTools)
                .build();
        this.operationalToolProvider = MethodToolCallbackProvider.builder()
                .toolObjects(readOnlyAgentTools, actionPreviewAgentTools)
                .build();
        this.readOnlyAgentTools = readOnlyAgentTools;
        this.actionPreviewAgentTools = actionPreviewAgentTools;
        this.approvalService = approvalService;
        this.intentGuard = intentGuard;
        ObjectMapper om = objectMapperProvider.getIfAvailable();
        this.objectMapper = om != null ? om : new ObjectMapper();
    }

    @Override
    public AgentReply chat(String message) {
        if (chatClient == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Conversational agent is unavailable. Configure NVIDIA_API_KEY and AGENT_LLM_PROVIDER=openai.");
        }
        if (isCapabilityQuestion(message)) {
            return new AgentReply(capabilityAnswer(),
                    "Capability information only. No ERP tool was called and no change was prepared.", null);
        }
        intentGuard.begin(message);
        try {
            Instant requestStartedAt = Instant.now();
            String role = SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
                    .findFirst().map(Object::toString).orElse("UNKNOWN");
            String answer;
            if (!intentGuard.actionsAllowed() && isAnalyticsQuestion(message)) {
                answer = formatToolResult(message, readOnlyAgentTools.askBusinessQuestion(message));
            } else {
                answer = chatClient.prompt()
                        .user("Authenticated role (trusted context): " + role + "\nUser request: " + message)
                        // Confirmation tools are intentionally absent from both allow-lists.
                        .tools(intentGuard.actionsAllowed() ? operationalToolProvider : readOnlyToolProvider)
                        .call()
                        .content();
                answer = processPotentialToolCall(answer, message);
            }

            ApprovalService.ActionPreview action = approvalService
                    .latestForCurrentCallerSince(requestStartedAt).orElse(null);
            if (action != null) {
                answer = "I prepared an operational action for your review. No business data has changed yet. "
                        + "Review the impact below, then confirm or reject it.";
            } else if (looksLikeRawToolCall(answer)) {
                answer = "I could not safely translate the tool response into a business answer. "
                        + "No action was executed; please refine the request and try again.";
            }

            return new AgentReply(answer,
                    action == null
                            ? "Live ERP data was consulted. No changes were made."
                            : "A change was prepared, but nothing has been executed. Review the approval card.",
                    action);
        }
        catch (ResponseStatusException exception) {
            if (exception.getStatusCode().value() == 400) {
                return new AgentReply(exception.getReason(),
                        "The request was blocked before execution. No business data was changed.", null);
            }
            if (exception.getStatusCode().value() == 401 || exception.getStatusCode().value() == 403) {
                log.error("A read-only assistant dependency rejected its internal authenticated call", exception);
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                        "A downstream ERP tool rejected the assistant call. Your IntelliOps session remains valid.", exception);
            }
            throw exception;
        }
        catch (Exception exception) {
            log.error("Conversational assistant request failed", exception);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "The NVIDIA agent or a downstream ERP service could not complete the request.", exception);
        }
        finally {
            intentGuard.clear();
        }
    }

    private String processPotentialToolCall(String answer, String userMessage) {
        if (answer == null) {
            return answer;
        }
        String narratedTool = narratedToolName(answer);
        if (narratedTool != null) {
            String toolResult = switch (narratedTool) {
                case "askBusinessQuestion" -> readOnlyAgentTools.askBusinessQuestion(userMessage);
                case "listProducts" -> readOnlyAgentTools.listProducts();
                default -> null;
            };
            if (toolResult != null) return formatToolResult(userMessage, toolResult);
        }
        if (!answer.contains("{")) return answer;
        try {
            int start = answer.indexOf("{");
            int end = answer.lastIndexOf("}");
            if (start < 0 || end <= start) {
                return answer;
            }
            String jsonStr = answer.substring(start, end + 1);
            JsonNode node = objectMapper.readTree(jsonStr);

            String toolName = null;
            if (node.has("name")) {
                toolName = node.get("name").asText();
            } else if (node.has("operationId") || node.has("service")) {
                toolName = "executeOpenApiRead";
            }

            if (toolName == null) {
                return answer;
            }

            log.info("Intercepted pseudo tool call JSON from LLM: tool={}, json={}", toolName, jsonStr);
            String toolResult = null;
            JsonNode params = node.has("parameters") ? node.get("parameters") : node;

            switch (toolName) {
                case "listProducts" -> toolResult = readOnlyAgentTools.listProducts();
                case "listOpenApiReadOperations" -> toolResult = readOnlyAgentTools.listOpenApiReadOperations();
                case "executeOpenApiRead" -> {
                    String service = params.has("service") ? params.get("service").asText() : "";
                    String operationId = params.has("operationId") ? params.get("operationId").asText() : "";
                    Map<String, String> pathParams = parseMap(params, "pathParameters");
                    Map<String, String> queryParams = parseMap(params, "queryParameters");
                    toolResult = readOnlyAgentTools.executeOpenApiRead(service, operationId, pathParams, queryParams);
                }
                case "getInventory" -> {
                    Long storeId = longValue(params, "storeId", "idBoutique");
                    Long productId = longValue(params, "productId", "idProduit");
                    if (storeId == null || productId == null) return "Please provide both the store/location ID and product ID.";
                    toolResult = readOnlyAgentTools.getInventory(storeId, productId);
                }
                case "getLead" -> {
                    Long leadId = longValue(params, "leadId", "idLead");
                    if (leadId == null) return "Please provide the lead ID you want to inspect.";
                    toolResult = readOnlyAgentTools.getLead(leadId);
                }
                case "listAgentLeads" -> {
                    Long agentId = longValue(params, "agentId");
                    if (agentId == null) return "Please provide the CSM agent ID, or ask for your own assigned leads.";
                    toolResult = readOnlyAgentTools.listAgentLeads(agentId);
                }
                case "askBusinessQuestion" -> {
                    String q = params.has("question") ? params.get("question").asText() : userMessage;
                    toolResult = readOnlyAgentTools.askBusinessQuestion(q);
                }
                case "previewStockAdjustment" -> {
                    ApprovalService.ActionPreview preview = actionPreviewAgentTools.previewStockAdjustment(
                            longValue(params, "storeId", "idBoutique"),
                            longValue(params, "productId", "idProduit"),
                            intValue(params, "quantity", "quantite"),
                            textValue(params, "movementType", "typeMouvement"));
                    toolResult = objectMapper.writeValueAsString(preview);
                }
                case "previewLeadConversion" -> {
                    Long leadId = longValue(params, "leadId", "idLead");
                    Long locationId = longValue(params, "stockLocationId", "locationId");
                    List<org.example.mcpserver.tools.LeadMcpTools.ItemRequest> items = objectMapper.convertValue(
                            params.path("items"), objectMapper.getTypeFactory().constructCollectionType(
                                    List.class, org.example.mcpserver.tools.LeadMcpTools.ItemRequest.class));
                    toolResult = objectMapper.writeValueAsString(
                            actionPreviewAgentTools.previewLeadConversion(leadId, locationId, items));
                }
                case "listAvailableOperations" -> toolResult = actionPreviewAgentTools.listAvailableOperations();
                case "previewOpenApiMutation" -> toolResult = objectMapper.writeValueAsString(
                        actionPreviewAgentTools.previewOpenApiMutation(
                                textValue(params, "service"), textValue(params, "operationId"),
                                parseMap(params, "pathParameters"), parseMap(params, "queryParameters"),
                                textValue(params, "requestBodyJson")));
            }

            if (toolResult != null) {
                log.info("Successfully executed tool {} via fallback interceptor, result length={}", toolName, toolResult.length());
                return formatToolResult(userMessage, toolResult);
            }
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (Exception e) {
            log.warn("Could not parse or execute intercepted tool call JSON: {}", e.getMessage());
        }

        return answer;
    }

    private Map<String, String> parseMap(JsonNode parent, String fieldName) {
        Map<String, String> result = new HashMap<>();
        if (parent.has(fieldName) && parent.get(fieldName).isObject()) {
            parent.get(fieldName).fields().forEachRemaining(entry ->
                    result.put(entry.getKey(), entry.getValue().asText()));
        }
        return result;
    }

    private String textValue(JsonNode node, String... names) {
        for (String name : names) {
            if (node.has(name)) return node.get(name).asText();
        }
        return "";
    }

    private Long longValue(JsonNode node, String... names) {
        for (String name : names) {
            if (node.has(name)) return node.get(name).asLong();
        }
        return null;
    }

    private int intValue(JsonNode node, String... names) {
        for (String name : names) {
            if (node.has(name)) return node.get(name).asInt();
        }
        return 0;
    }

    private boolean looksLikeRawToolCall(String answer) {
        if (answer == null) return false;
        String value = answer.trim();
        return value.startsWith("{") && (value.contains("\"name\"")
                || value.contains("\"parameters\"") || value.contains("\"operationId\""));
    }

    private String narratedToolName(String answer) {
        String normalized = answer.toLowerCase();
        if (normalized.contains("askbusinessquestion")) return "askBusinessQuestion";
        if (normalized.contains("listproducts")) return "listProducts";
        return null;
    }

    private String formatToolResult(String userMessage, String toolResult) {
        String formatPrompt = String.format("""
                The user asked: "%s"
                The trusted ERP backend returned:

                %s

                Answer the question using only this result. Use concise natural-language markdown.
                Never output JSON, tool names, function-call narration, or invented values.
                """, userMessage, toolResult);
        String formatted = chatClient.prompt().user(formatPrompt).call().content();
        if (looksLikeRawToolCall(formatted) || narratedToolName(formatted) != null) {
            return safeBackendFallback(toolResult);
        }
        return formatted;
    }

    private String safeBackendFallback(String toolResult) {
        try {
            JsonNode result = objectMapper.readTree(toolResult);
            for (String field : List.of("answer", "summary", "message")) {
                if (result.hasNonNull(field) && result.get(field).isTextual()) {
                    return result.get(field).asText();
                }
            }
        } catch (Exception ignored) {
            // Never fall back to displaying untrusted raw JSON or a function-call transcript.
        }
        return "The ERP returned data, but the model could not safely format it. No values were invented and no change was made.";
    }

    private boolean isAnalyticsQuestion(String message) {
        String value = message.toLowerCase();
        return value.matches(".*\\b(revenue|sales|turnover|metric|trend|ranking|orders by status|stock value|chiffre d.?affaires)\\b.*");
    }

    private boolean isCapabilityQuestion(String message) {
        String value = message.trim().toLowerCase();
        return value.matches("(what|which|show|tell me)?\\s*(can you do|you can do|are your capabilities|actions can you|can the assistant do).*" )
                || value.matches(".*\\b(capabilities|available actions|help menu)\\b.*");
    }

    private String capabilityAnswer() {
        return """
                **I can help in three controlled modes:**

                - **Investigate:** query live leads, orders, products, inventory, deliveries, payments, notifications, and subscriptions.
                - **Analyze:** answer BI questions about revenue, order status, stock, rankings, and trends.
                - **Prepare actions:** propose role-authorized changes such as stock adjustments, lead conversion, order workflow, delivery actions, or payment operations.

                I never invent a target and never execute a write from chat alone. For a change, provide the exact resource ID and values; I will produce an approval card for you to review.
                """;
    }

    public record AgentReply(String answer, String safety, ApprovalService.ActionPreview action) {
        public AgentReply(String answer, String safety) {
            this(answer, safety, null);
        }
    }
}
