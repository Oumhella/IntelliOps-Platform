package org.example.mcpserver.agent;

import org.example.mcpserver.approval.ApprovalService;
import org.example.mcpserver.tools.LeadMcpTools;
import org.example.mcpserver.tools.OpenApiMcpTools;
import org.example.mcpserver.tools.StockMcpTools;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentActionServiceTest {
    @Test
    void dispatchesAStockConfirmationThroughTheExistingMcpTool() {
        ApprovalService approvals = mock(ApprovalService.class);
        StockMcpTools stock = mock(StockMcpTools.class);
        LeadMcpTools leads = mock(LeadMcpTools.class);
        OpenApiMcpTools openApi = mock(OpenApiMcpTools.class);
        when(approvals.operationFor("token")).thenReturn("STOCK_ADJUSTMENT");
        when(stock.confirmerAjustementStock("token", "CONFIRM")).thenReturn("{\"quantity\":12}");

        AgentActionService.ActionExecution result = new AgentActionService(approvals, stock, leads, openApi)
                .confirm("token");

        assertEquals("STOCK_ADJUSTMENT", result.operation());
        assertEquals("Stock adjustment completed successfully.", result.message());
        verify(stock).confirmerAjustementStock("token", "CONFIRM");
    }
}
