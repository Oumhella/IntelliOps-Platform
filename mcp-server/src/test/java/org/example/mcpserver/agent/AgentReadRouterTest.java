package org.example.mcpserver.agent;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentReadRouterTest {
    private ReadOnlyAgentTools tools;
    private PlatformReadTools platformTools;
    private AgentReadRouter router;

    @BeforeEach
    void setUp() {
        tools = mock(ReadOnlyAgentTools.class);
        platformTools = mock(PlatformReadTools.class);
        router = new AgentReadRouter(tools, platformTools);
    }

    @Test
    void capabilityQuestionNeverCallsBusinessTools() {
        var result = router.route("what else you can do").orElseThrow();

        assertThat(result.isDirect()).isTrue();
        assertThat(result.directAnswer()).contains("Investigate", "Analyze", "Prepare operations");
        verify(tools, never()).listProducts();
        verify(tools, never()).listVisibleLeads();
    }

    @Test
    void greetingDoesNotLeakIntoSubscriptionOrOtherBusinessTools() {
        var result = router.route("heey").orElseThrow();

        assertThat(result.isDirect()).isTrue();
        assertThat(result.directAnswer()).startsWith("Hi!");
        verify(tools, never()).askBusinessQuestion("heey");
    }

    @Test
    void productListUsesTheDedicatedCatalogTool() {
        when(tools.listProducts()).thenReturn("[{\"idProduit\":1,\"nomProduit\":\"Gift Card\"}]");

        var result = router.route("show all available products").orElseThrow();

        assertThat(result.backendResult()).contains("Gift Card");
        verify(tools).listProducts();
    }

    @Test
    void genericLeadRequestUsesAuthenticatedVisibleQueueWithoutInventingAgentId() {
        when(tools.listVisibleLeads()).thenReturn("{\"content\":[]}");

        var result = router.route("show leads").orElseThrow();

        assertThat(result.backendResult()).isEqualTo("{\"content\":[]}");
        verify(tools).listVisibleLeads();
        verify(tools, never()).listAgentLeads(org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void explicitLeadIdUsesSingleLeadLookup() {
        when(tools.getLead(42L)).thenReturn("{\"idLead\":42}");

        router.route("show lead 42").orElseThrow();

        verify(tools).getLead(42L);
        verify(tools, never()).listVisibleLeads();
    }

    @Test
    void compoundRevenueAndOrderStatusQuestionExecutesBothMetrics() {
        when(tools.askBusinessQuestion("What is the current paid revenue?")).thenReturn("revenue-result");
        when(tools.askBusinessQuestion("How many orders are there by status?")).thenReturn("orders-result");

        var result = router.route("Show current revenue and orders by status").orElseThrow();

        assertThat(result.backendResult()).contains("revenue-result", "orders-result");
        verify(tools).askBusinessQuestion("What is the current paid revenue?");
        verify(tools).askBusinessQuestion("How many orders are there by status?");
    }

    @Test
    void inventoryWithoutBothAuthoritativeIdsRequestsClarification() {
        var result = router.route("show stock for product 12").orElseThrow();

        assertThat(result.isDirect()).isTrue();
        assertThat(result.directAnswer()).contains("product ID", "store/location ID");
        verify(tools, never()).getInventory(org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void operationalOrderListUsesStableFirstPartyEndpointAdapter() {
        when(platformTools.listOrders()).thenReturn("{\"content\":[{\"idCommande\":7}]}");

        var result = router.route("show my orders").orElseThrow();

        assertThat(result.backendResult()).contains("idCommande");
        verify(platformTools).listOrders();
    }

    @Test
    void currentPlanUsesAuthenticatedEntitlementInsteadOfModelGuessing() {
        when(platformTools.currentSubscription()).thenReturn("{\"planName\":\"Free\"}");

        router.route("show my current subscription plan").orElseThrow();

        verify(platformTools).currentSubscription();
    }

    @Test
    void semanticIntentSupportsFrenchWithoutRequiringACommandTemplate() {
        when(tools.listVisibleLeads()).thenReturn("{\"content\":[]}");
        var intent = new AgentIntentClassifier.ClassifiedIntent(
                AgentIntentClassifier.Intent.LIST_LEADS, null, null, null, null, 0.96);

        router.route("Est-ce que tu peux me montrer les prospects dont je dois m'occuper ?", intent)
                .orElseThrow();

        verify(tools).listVisibleLeads();
    }

    @Test
    void semanticIntentSupportsArabicAnalyticsAndCanonicalizesTheQuestion() {
        when(tools.askBusinessQuestion("What is the current revenue?")).thenReturn("revenue-result");
        var intent = new AgentIntentClassifier.ClassifiedIntent(
                AgentIntentClassifier.Intent.ANALYTICS, null, null, null,
                "What is the current revenue?", 0.94);

        var result = router.route("شنو هو رقم المعاملات الحالي؟", intent).orElseThrow();

        assertThat(result.backendResult()).isEqualTo("revenue-result");
        verify(tools).askBusinessQuestion("What is the current revenue?");
    }
}
