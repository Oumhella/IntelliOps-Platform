package org.example.mcpserver.agent;

import org.example.mcpserver.approval.ApprovalService;
import org.example.mcpserver.tools.LeadMcpTools;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Deterministic routing for high-value previews whose parameters are fully grounded in the request. */
@Component
public class AgentActionRouter {
    private static final Pattern LEAD_CONVERSION = Pattern.compile(
            "(?iu)(?:\\b(?:convert|convertir)\\b|\\b(?:prepare|preparer)\\b.{0,30}\\bconversion\\b|حضر.{0,30}تحويل)");
    private static final Pattern LEAD_ID = Pattern.compile(
            "(?iu)(?:\\b(?:lead|prospect)\\b|العميل\\s+المحتمل|عميل\\s+محتمل)"
                    + "(?:\\s+(?:qualifie|qualified|المؤهل))?\\s*(?:id|numero|n)?\\s*#?\\s*(\\d+)");
    private static final Pattern LOCATION_ID = Pattern.compile(
            "(?iu)(?:\\b(?:location|lieu|emplacement|site|store|boutique)\\b|الموقع|موقع)"
                    + "\\s*(?:id|numero|n)?\\s*#?\\s*(\\d+)");
    private static final Pattern ITEM = Pattern.compile(
            "(?iu)(?:\\b(?:product|produit)\\b|المنتج|منتج)\\s*(?:id|numero|n)?\\s*#?\\s*(\\d+)"
                    + ".{0,50}?(?:\\b(?:quantity|quantite|qty)\\b|الكمية|كمية)\\s*(\\d+)");

    private final ActionPreviewAgentTools previewTools;

    public AgentActionRouter(ActionPreviewAgentTools previewTools) {
        this.previewTools = previewTools;
    }

    public Optional<ApprovalService.ActionPreview> route(String message) {
        String normalized = normalize(message);
        if (!LEAD_CONVERSION.matcher(normalized).find()) return Optional.empty();

        Long leadId = firstLong(LEAD_ID, normalized);
        Long locationId = firstLong(LOCATION_ID, normalized);
        List<LeadMcpTools.ItemRequest> items = items(normalized);
        if (leadId == null || locationId == null || items.isEmpty()) return Optional.empty();

        return Optional.of(previewTools.previewLeadConversion(leadId, locationId, items));
    }

    private List<LeadMcpTools.ItemRequest> items(String message) {
        List<LeadMcpTools.ItemRequest> result = new ArrayList<>();
        Matcher matcher = ITEM.matcher(message);
        while (matcher.find()) {
            int quantity = Integer.parseInt(matcher.group(2));
            if (quantity > 0) result.add(new LeadMcpTools.ItemRequest(
                    Long.parseLong(matcher.group(1)), quantity));
        }
        return List.copyOf(result);
    }

    private Long firstLong(Pattern pattern, String message) {
        Matcher matcher = pattern.matcher(message);
        return matcher.find() ? Long.parseLong(matcher.group(1)) : null;
    }

    private String normalize(String message) {
        if (message == null) return "";
        return Normalizer.normalize(message, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT);
    }
}
