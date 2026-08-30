package org.example.mcpserver.agent;

import org.example.mcpserver.approval.ApprovalService;
import org.example.mcpserver.tools.LeadMcpTools;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentActionRouterTest {
    private ActionPreviewAgentTools tools;
    private AgentActionRouter router;

    @BeforeEach
    void setUp() {
        tools = mock(ActionPreviewAgentTools.class);
        router = new AgentActionRouter(tools);
    }

    @Test
    void routesNaturalFrenchLeadConversionWithGroundedParameters() {
        var items = List.of(new LeadMcpTools.ItemRequest(8L, 1));
        var preview = preview();
        when(tools.previewLeadConversion(2L, 1L, items)).thenReturn(preview);

        var result = router.route("Préparer la conversion du prospect qualifié 2 en commande "
                + "en utilisant le lieu 1 et le produit 8, quantité 1");

        assertThat(result).contains(preview);
        verify(tools).previewLeadConversion(2L, 1L, items);
    }

    @Test
    void routesArabicLeadConversionWithGroundedParameters() {
        var items = List.of(new LeadMcpTools.ItemRequest(8L, 2));
        when(tools.previewLeadConversion(2L, 1L, items)).thenReturn(preview());

        assertThat(router.route("حضّر تحويل العميل المحتمل 2 باستخدام الموقع 1 والمنتج 8 الكمية 2"))
                .isPresent();
        verify(tools).previewLeadConversion(2L, 1L, items);
    }

    @Test
    void neverPreviewsWhenARequiredGroundedValueIsMissing() {
        assertThat(router.route("Préparer la conversion du prospect 2 avec le produit 8")).isEmpty();
        verify(tools, never()).previewLeadConversion(
                org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyList());
    }

    @Test
    void discussingAConversionDoesNotCreateAPreview() {
        assertThat(router.route("Explique la conversion du prospect 2 avec le lieu 1, "
                + "le produit 8 et la quantité 1")).isEmpty();
        verify(tools, never()).previewLeadConversion(
                org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyList());
    }

    private ApprovalService.ActionPreview preview() {
        return new ApprovalService.ActionPreview("token", "LEAD_CONVERSION", "summary",
                Instant.now().plusSeconds(300), true, ApprovalService.RiskLevel.MEDIUM,
                false, "Review and confirm");
    }
}
