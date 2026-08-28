package org.example.mcpserver.agent;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.Mockito.when;

@WebMvcTest(AgentController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(AgentControllerTest.TestAgentConfig.class)
class AgentControllerTest {
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private AgentActionService agentActionService;

    @Test
    void chatForwardsAValidMessageToTheReadOnlyAgent() throws Exception {
        mockMvc.perform(post("/api/v1/agent/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"Show inventory for product 12 in store 3\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value("Inventory is available."))
                .andExpect(jsonPath("$.safety").value("No changes were made."));
    }

    @Test
    void chatRejectsBlankMessagesBeforeCallingTheModel() throws Exception {
        mockMvc.perform(post("/api/v1/agent/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"   \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void authenticatedUiCanConfirmAStructuredApprovalToken() throws Exception {
        when(agentActionService.confirm("approval-123", "CONFIRM", null)).thenReturn(
                new AgentActionService.ActionExecution("STOCK_ADJUSTMENT", "Stock updated.", "{}"));

        mockMvc.perform(post("/api/v1/agent/actions/approval-123/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirmation\":\"CONFIRM\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.operation").value("STOCK_ADJUSTMENT"))
                .andExpect(jsonPath("$.message").value("Stock updated."));
    }

    @TestConfiguration
    static class TestAgentConfig {
        @Bean
        AgentChat agentChat() {
            return message -> new AgentChatService.AgentReply("Inventory is available.", "No changes were made.");
        }

        @Bean
        AgentActionService agentActionService() {
            return org.mockito.Mockito.mock(AgentActionService.class);
        }
    }
}
