package org.example.mcpserver.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.Map;

@Service
public class AgentChatService implements AgentChat {
    private static final Logger log = LoggerFactory.getLogger(AgentChatService.class);
    private static final String SYSTEM_PROMPT = """
            You are the ERP IntelliOps operations assistant. Help users understand current ERP
            data across inventory, products, leads, orders, users, subscriptions, payments,
            deliveries, and notifications using the supplied read-only tools.

            TOOL ROUTING:
            - Products / product list / available products / catalog -> ALWAYS call listProducts.
            - Inventory / store stock -> call getInventory.
            - CRM leads -> call getLead or listAgentLeads.
            - Business trends, metrics, rankings, revenue -> call askBusinessQuestion.
            - Other read operations -> call listOpenApiReadOperations then executeOpenApiRead.

            RESPONSE RULES:
            1. ALWAYS call the tool to get the real ERP data.
            2. Present all information in clean, professional natural language markdown (bullet points, bold headings, or markdown tables).
            3. NEVER output raw JSON, function call parameter JSON like {"name": "..."}, or tool signatures to the user.
            4. You cannot perform write/mutation operations. If asked to modify data, explain that mutations require the separate MCP preview and confirmation workflow.
            """;

    private final ChatClient chatClient;
    private final ToolCallbackProvider readOnlyToolProvider;
    private final ReadOnlyAgentTools readOnlyAgentTools;
    private final ObjectMapper objectMapper;

    public AgentChatService(ObjectProvider<ChatModel> chatModelProvider, ReadOnlyAgentTools readOnlyAgentTools, ObjectProvider<ObjectMapper> objectMapperProvider) {
        ChatModel chatModel = chatModelProvider.getIfAvailable();
        this.chatClient = chatModel == null ? null : ChatClient.builder(chatModel)
                .defaultSystem(SYSTEM_PROMPT)
                .build();
        this.readOnlyToolProvider = MethodToolCallbackProvider.builder()
                .toolObjects(readOnlyAgentTools)
                .build();
        this.readOnlyAgentTools = readOnlyAgentTools;
        ObjectMapper om = objectMapperProvider.getIfAvailable();
        this.objectMapper = om != null ? om : new ObjectMapper();
    }

    @Override
    public AgentReply chat(String message) {
        if (chatClient == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Conversational agent is unavailable. Configure NVIDIA_API_KEY and AGENT_LLM_PROVIDER=openai.");
        }
        try {
            String answer = chatClient.prompt()
                    .user(message)
                    // This explicit provider is the only tool allow-list for this endpoint.
                    .tools(readOnlyToolProvider)
                    .call()
                    .content();

            answer = processPotentialToolCall(answer, message);

            return new AgentReply(answer,
                    "This endpoint has access only to read-only ERP tools. No changes were made.");
        }
        catch (ResponseStatusException exception) {
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
    }

    private String processPotentialToolCall(String answer, String userMessage) {
        if (answer == null || !answer.contains("{")) {
            return answer;
        }
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
                    Long storeId = params.has("storeId") ? params.get("storeId").asLong() : 1L;
                    Long productId = params.has("productId") ? params.get("productId").asLong() : 1L;
                    toolResult = readOnlyAgentTools.getInventory(storeId, productId);
                }
                case "getLead" -> {
                    Long leadId = params.has("leadId") ? params.get("leadId").asLong() : 1L;
                    toolResult = readOnlyAgentTools.getLead(leadId);
                }
                case "listAgentLeads" -> {
                    Long agentId = params.has("agentId") ? params.get("agentId").asLong() : 1L;
                    toolResult = readOnlyAgentTools.listAgentLeads(agentId);
                }
                case "askBusinessQuestion" -> {
                    String q = params.has("question") ? params.get("question").asText() : userMessage;
                    toolResult = readOnlyAgentTools.askBusinessQuestion(q);
                }
            }

            if (toolResult != null) {
                log.info("Successfully executed tool {} via fallback interceptor, result length={}", toolName, toolResult.length());
                String formatPrompt = String.format("""
                        The user asked: "%s"
                        The ERP backend system returned the following data:
                        
                        %s
                        
                        Summarize and format this data clearly for the user using natural language markdown (bullet points, bold headers, or tables). Do NOT output raw JSON or code.
                        """, userMessage, toolResult);

                return chatClient.prompt()
                        .user(formatPrompt)
                        .call()
                        .content();
            }
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

    public record AgentReply(String answer, String safety) { }
}
