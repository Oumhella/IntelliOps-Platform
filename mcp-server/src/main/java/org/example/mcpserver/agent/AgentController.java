package org.example.mcpserver.agent;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Stable entry point for a future conversational agent. It deliberately exposes
 * no "chat that can write" endpoint: the MCP approval workflow remains the only
 * path for mutations.
 */
@RestController
@RequestMapping("/api/v1/agent")
public class AgentController {
    private final boolean enabled;
    private final boolean nvidiaKeyConfigured;
    private final String model;
    private final AgentChat agentChatService;

    public AgentController(
            @Value("${agent.llm.provider:none}") String provider,
            @Value("${NVIDIA_API_KEY:}") String nvidiaApiKey,
            @Value("${NVIDIA_MODEL:meta/llama-3.1-70b-instruct}") String model,
            AgentChat agentChatService) {
        this.enabled = "openai".equalsIgnoreCase(provider);
        this.nvidiaKeyConfigured = !nvidiaApiKey.isBlank();
        this.model = model;
        this.agentChatService = agentChatService;
    }

    @GetMapping("/status")
    public AgentStatus status() {
        return new AgentStatus(enabled, nvidiaKeyConfigured, model,
                enabled && nvidiaKeyConfigured ? "ready for read-only conversational requests" : "waiting for AGENT_LLM_PROVIDER=openai and NVIDIA_API_KEY",
                List.of("consult inventory", "list products", "consult leads", "list agent leads"),
                "Write operations require a separate MCP preview and explicit CONFIRM call.");
    }

    @PostMapping("/chat")
    public AgentChatService.AgentReply chat(@RequestBody ChatRequest request) {
        if (request == null || request.message() == null || request.message().isBlank()) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST, "message is required");
        }
        if (request.message().length() > 4_000) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST, "message must not exceed 4000 characters");
        }
        return agentChatService.chat(request.message().trim());
    }

    public record AgentStatus(boolean enabled, boolean nvidiaApiKeyConfigured, String model, String state,
                              List<String> readOnlyCapabilities, String mutationSafety) { }
    public record ChatRequest(String message) { }
}
