package org.example.mcpserver.agent;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    public AgentChatService(ObjectProvider<ChatModel> chatModelProvider, ReadOnlyAgentTools readOnlyAgentTools) {
        ChatModel chatModel = chatModelProvider.getIfAvailable();
        this.chatClient = chatModel == null ? null : ChatClient.builder(chatModel)
                .defaultSystem(SYSTEM_PROMPT)
                .build();
        this.readOnlyToolProvider = MethodToolCallbackProvider.builder()
                .toolObjects(readOnlyAgentTools)
                .build();
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

    public record AgentReply(String answer, String safety) { }
}
