package org.example.mcpserver.agent;

public interface AgentChat {
    AgentChatService.AgentReply chat(String message);

    default AgentChatService.AgentReply chat(String message, String locale) {
        return chat(message);
    }
}
