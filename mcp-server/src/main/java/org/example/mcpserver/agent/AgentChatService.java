package org.example.mcpserver.agent;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AgentChatService implements AgentChat {
    private static final String SYSTEM_PROMPT = """
            You are the ERP IntelliOps operations assistant. Help users understand inventory,
            products, leads, and CSM workload using the supplied read-only tools when current ERP
            data is needed. Be concise, state identifiers used, and distinguish facts returned by
            tools from recommendations. You cannot create orders, adjust stock, reserve stock, or
            execute any other mutation. If asked to change data, explain that a human must use the
            separate MCP preview and explicit confirmation workflow; never suggest that a change
            has been made.
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
                    .toolCallbacks(readOnlyToolProvider)
                    .call()
                    .content();
            return new AgentReply(answer,
                    "This endpoint has access only to read-only ERP tools. No changes were made.");
        }
        catch (ResponseStatusException exception) {
            throw exception;
        }
        catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "The NVIDIA agent or a downstream ERP service could not complete the request.", exception);
        }
    }

    public record AgentReply(String answer, String safety) { }
}
