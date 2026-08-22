package org.example.mcpserver.tools;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class AnalyticsMcpTools {
    private final RestClient analyticsServiceClient;

    public AnalyticsMcpTools(@Qualifier("analyticsServiceClient") RestClient analyticsServiceClient) {
        this.analyticsServiceClient = analyticsServiceClient;
    }

    @Tool(description = "Read-only: answers aggregated business intelligence questions about "
            + "revenue, orders, products and stock using the authenticated enterprise's reporting data.")
    public String askBusinessQuestion(
            @ToolParam(description = "Business question in plain language") String question) {
        if (question == null || question.isBlank() || question.length() > 1000) {
            throw new IllegalArgumentException("Business question must contain 1 to 1000 characters.");
        }
        return analyticsServiceClient.post()
                .uri("/api/v1/analytics/ask")
                .body(new AnalyticsQuestion(question.trim()))
                .retrieve()
                .body(String.class);
    }

    private record AnalyticsQuestion(String question) { }
}
