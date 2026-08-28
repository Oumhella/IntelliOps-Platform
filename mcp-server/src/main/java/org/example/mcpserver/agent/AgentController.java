package org.example.mcpserver.agent;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PathVariable;

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
    private final AgentActionService agentActionService;

    public AgentController(
            @Value("${agent.llm.provider:none}") String provider,
            @Value("${spring.ai.openai.api-key:}") String openAiApiKey,
            @Value("${spring.ai.openai.chat.options.model:meta/llama-3.1-70b-instruct}") String model,
            AgentChat agentChatService,
            AgentActionService agentActionService) {
        this.enabled = "openai".equalsIgnoreCase(provider);
        this.nvidiaKeyConfigured = !openAiApiKey.isBlank();
        this.model = model;
        this.agentChatService = agentChatService;
        this.agentActionService = agentActionService;
    }

    @GetMapping("/status")
    public AgentStatus status() {
        return new AgentStatus(enabled, nvidiaKeyConfigured, model,
                enabled && nvidiaKeyConfigured ? "ready for operational requests" : "waiting for AGENT_LLM_PROVIDER=openai and NVIDIA_API_KEY",
                List.of("all documented read-only Swagger operations", "business intelligence", "consult inventory", "list products", "consult leads", "list agent leads"),
                List.of("preview stock adjustments", "preview lead conversion", "preview role-authorized ERP operations"),
                "The assistant can prepare changes. Only the authenticated user can confirm the approval card; domain permissions still apply.");
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
        String locale = request.locale() != null && List.of("en", "fr", "ar").contains(request.locale())
                ? request.locale() : "en";
        return agentChatService.chat(request.message().trim(), locale);
    }

    @PostMapping("/actions/{token}/confirm")
    public AgentActionService.ActionExecution confirm(@PathVariable String token,
                                                       @RequestBody(required = false) ActionConfirmation request) {
        String confirmation = request == null ? "" : request.confirmation();
        String reason = request == null ? null : request.reason();
        return agentActionService.confirm(token, confirmation, reason);
    }

    @PostMapping("/actions/{token}/reject")
    public AgentActionService.ActionExecution reject(@PathVariable String token) {
        return agentActionService.reject(token);
    }

    public record AgentStatus(boolean enabled, boolean nvidiaApiKeyConfigured, String model, String state,
                              List<String> readOnlyCapabilities, List<String> actionCapabilities,
                              String mutationSafety) { }
    public record ChatRequest(String message, String locale) { }
    public record ActionConfirmation(String confirmation, String reason) { }
}
