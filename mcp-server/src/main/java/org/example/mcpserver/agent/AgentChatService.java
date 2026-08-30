package org.example.mcpserver.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.mcpserver.approval.ApprovalService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Service
public class AgentChatService implements AgentChat {
    private static final Logger log = LoggerFactory.getLogger(AgentChatService.class);
    private static final String SYSTEM_PROMPT = """
            You are the ERP IntelliOps operations assistant. Help users understand current ERP
            data and safely operate inventory, CRM, orders, payments, deliveries, and other
            documented workflows using the supplied tools.

            TOOL ROUTING:
            - Products / product list / available products / catalog -> ALWAYS call listProducts.
            - Inventory / store stock -> call getInventory.
            - CRM leads -> call getLead or listAgentLeads.
            - Business trends, metrics, rankings, revenue -> call askBusinessQuestion.
            - Other read operations -> call listOpenApiReadOperations then executeOpenApiRead.
            - Stock adjustment -> inspect inventory, then call previewStockAdjustment.
            - Qualified lead conversion -> inspect the lead and products, then call previewLeadConversion.
            - Other business changes -> call listAvailableOperations, select the exact non-GET operation,
              then call previewOpenApiMutation.

            RESPONSE RULES:
            1. For data questions, ALWAYS call a tool to get real ERP data. For capability questions,
               explain available capabilities directly without calling any business tool.
            2. Present all information in clean, professional natural language markdown (bullet points, bold headings, or markdown tables).
            3. NEVER output raw JSON, function call parameter JSON like {"name": "..."}, or tool signatures to the user.
            4. You may PREVIEW a write operation, but you can never confirm or execute it. After a preview,
               explain the impact and tell the user to use the approval card. Never claim that a preview changed data.
            5. Respect the authenticated role: CSM handles assigned leads and customer/order handoff;
               LOGISTIC handles stock, preparation and assignment; LIVREUR handles only assigned delivery
               execution; ADMIN handles workspace administration. Domain APIs enforce the final permission.
            6. NEVER invent an ID, amount, customer, order, payment, product, location, or status. If a write
               request lacks an exact target or required value, ask a concise clarification question.
            """;

    private final ChatClient chatClient;
    private final ToolCallbackProvider operationalToolProvider;
    private final ReadOnlyAgentTools readOnlyAgentTools;
    private final AgentReadRouter readRouter;
    private final AgentIntentClassifier intentClassifier;
    private final AgentActionRouter actionRouter;
    private final ActionPreviewAgentTools actionPreviewAgentTools;
    private final ApprovalService approvalService;
    private final AgentActionIntentGuard intentGuard;
    private final ObjectMapper objectMapper;

    public AgentChatService(ObjectProvider<ChatModel> chatModelProvider,
                            ReadOnlyAgentTools readOnlyAgentTools,
                            AgentReadRouter readRouter,
                            AgentIntentClassifier intentClassifier,
                            AgentActionRouter actionRouter,
                            ActionPreviewAgentTools actionPreviewAgentTools,
                            ApprovalService approvalService,
                            AgentActionIntentGuard intentGuard,
                            ObjectProvider<ObjectMapper> objectMapperProvider) {
        ChatModel chatModel = chatModelProvider.getIfAvailable();
        this.chatClient = chatModel == null ? null : ChatClient.builder(chatModel)
                .defaultSystem(SYSTEM_PROMPT)
                .build();
        this.operationalToolProvider = MethodToolCallbackProvider.builder()
                .toolObjects(readOnlyAgentTools, actionPreviewAgentTools)
                .build();
        this.readOnlyAgentTools = readOnlyAgentTools;
        this.readRouter = readRouter;
        this.intentClassifier = intentClassifier;
        this.actionRouter = actionRouter;
        this.actionPreviewAgentTools = actionPreviewAgentTools;
        this.approvalService = approvalService;
        this.intentGuard = intentGuard;
        ObjectMapper om = objectMapperProvider.getIfAvailable();
        this.objectMapper = om != null ? om : new ObjectMapper();
    }

    @Override
    public AgentReply chat(String message) {
        return chat(message, "en");
    }

    @Override
    public AgentReply chat(String message, String locale) {
        String responseLocale = normalizeLocale(locale);
        intentGuard.begin(message);
        try {
            var deterministicAction = actionRouter.route(message)
                    .map(action -> localizePreview(action, responseLocale));
            if (deterministicAction.isPresent()) {
                return new AgentReply(
                        localized(responseLocale,
                                "I prepared the lead conversion for your review. Verify the target, location, product, and quantity before confirming.",
                                "J’ai préparé la conversion du prospect. Vérifiez le prospect, le lieu, le produit et la quantité avant de confirmer.",
                                "حضّرت تحويل العميل المحتمل للمراجعة. تحقق من العميل والموقع والمنتج والكمية قبل التأكيد."),
                        localized(responseLocale,
                                "A change was prepared, but nothing has been executed. Review the approval card.",
                                "Une modification a été préparée, mais rien n’a été exécuté. Vérifiez la carte d’approbation.",
                                "تم تحضير تغيير دون تنفيذه. راجع بطاقة الموافقة."),
                        deterministicAction.get());
            }
            if (!intentGuard.actionsAllowed()) {
                var routed = readRouter.route(message);
                if (routed.isEmpty()) {
                    routed = intentClassifier.classify(message)
                            .flatMap(intent -> readRouter.route(message, intent));
                }
                if (routed.isPresent()) {
                    AgentReadRouter.RoutedRead read = routed.get();
                    if (read.isDirect()) {
                        return new AgentReply(localizeDirect(read.directAnswer(), responseLocale),
                                localized(responseLocale,
                                        "No ERP data was changed and no operation was prepared.",
                                        "Aucune donnée ERP n’a été modifiée et aucune opération n’a été préparée.",
                                        "لم تتغير أي بيانات في نظام ERP ولم تُحضّر أي عملية."), null);
                    }
                    requireChatModel();
                    return new AgentReply(formatToolResult(message, read.backendResult(), responseLocale),
                            localized(responseLocale,
                                    "Live, permission-scoped ERP data was consulted. No changes were made.",
                                    "Les données ERP en temps réel autorisées pour votre rôle ont été consultées. Aucun changement n’a été effectué.",
                                    "تم الرجوع إلى بيانات ERP الفعلية المسموح بها لدورك، ولم يُجرَ أي تغيير."), null);
                }
                return new AgentReply(localized(responseLocale,
                                "I could not map that request to a safe, authoritative ERP query. Please name the resource and scope—for example, ‘show available products’, ‘show my leads’, ‘inventory for product 12 at location 3’, or ‘orders by status’.",
                                "Je n’ai pas pu associer cette demande à une requête ERP sûre et fiable. Précisez la ressource et le périmètre, par exemple : « afficher les produits disponibles », « afficher mes prospects », « stock du produit 12 au lieu 3 » ou « commandes par statut ».",
                                "تعذر ربط الطلب باستعلام آمن وموثوق في نظام ERP. حدّد المورد والنطاق، مثل: «عرض المنتجات المتاحة» أو «عرض العملاء المحتملين المسندين إليّ» أو «مخزون المنتج 12 في الموقع 3» أو «الطلبات حسب الحالة»."),
                        localized(responseLocale,
                                "No tool was called and no business data was changed.",
                                "Aucun outil n’a été appelé et aucune donnée métier n’a été modifiée.",
                                "لم تُستدعَ أي أداة ولم تتغير أي بيانات أعمال."), null);
            }

            requireChatModel();
            Instant requestStartedAt = Instant.now();
            String role = SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
                    .findFirst().map(Object::toString).orElse("UNKNOWN");
            String answer = chatClient.prompt()
                    .user("Authenticated role (trusted context): " + role
                            + "\nRespond in locale: " + responseLocale + "\nUser request: " + message)
                    // Confirmation tools are intentionally absent from the allow-list.
                    .tools(operationalToolProvider)
                    .call()
                    .content();
            answer = processPotentialToolCall(answer, message, responseLocale);

            ApprovalService.ActionPreview action = approvalService
                    .latestForCurrentCallerSince(requestStartedAt).orElse(null);
            action = localizePreview(action, responseLocale);
            if (action != null) {
                answer = localized(responseLocale,
                        "I prepared an operational action for your review. No business data has changed yet. Review the impact below, then confirm or reject it.",
                        "J’ai préparé une opération pour votre vérification. Aucune donnée métier n’a encore changé. Vérifiez l’impact ci-dessous, puis confirmez ou refusez.",
                        "حضّرت عملية لمراجعتك، ولم تتغير أي بيانات أعمال بعد. راجع الأثر أدناه ثم أكّد العملية أو ارفضها.");
            } else if (looksLikeRawToolCall(answer)) {
                answer = localized(responseLocale,
                        "I could not safely translate the tool response into a business answer. No action was executed; please refine the request and try again.",
                        "Je n’ai pas pu convertir la réponse de l’outil en réponse métier sûre. Aucune opération n’a été exécutée ; précisez la demande et réessayez.",
                        "تعذر تحويل استجابة الأداة إلى إجابة أعمال آمنة. لم تُنفذ أي عملية؛ وضّح الطلب ثم أعد المحاولة.");
            }

            return new AgentReply(answer,
                    action == null
                            ? localized(responseLocale, "Live ERP data was consulted. No changes were made.",
                                    "Les données ERP en temps réel ont été consultées. Aucun changement n’a été effectué.",
                                    "تم الرجوع إلى بيانات ERP الفعلية، ولم يُجرَ أي تغيير.")
                            : localized(responseLocale, "A change was prepared, but nothing has been executed. Review the approval card.",
                                    "Une modification a été préparée, mais rien n’a été exécuté. Vérifiez la carte d’approbation.",
                                    "تم تحضير تغيير دون تنفيذه. راجع بطاقة الموافقة."),
                    action);
        }
        catch (ResponseStatusException exception) {
            if (exception.getStatusCode().value() == 400) {
                return new AgentReply(localizeBlockedReason(exception.getReason(), responseLocale),
                        localized(responseLocale,
                                "The request was blocked before execution. No business data was changed.",
                                "La demande a été bloquée avant exécution. Aucune donnée métier n’a été modifiée.",
                                "حُظر الطلب قبل التنفيذ ولم تتغير أي بيانات أعمال."), null);
            }
            if (exception.getStatusCode().value() == 403) {
                return new AgentReply(localized(responseLocale,
                                "Your authenticated role is not permitted to view that resource.",
                                "Votre rôle authentifié n’est pas autorisé à consulter cette ressource.",
                                "دورك الموثق غير مخول لعرض هذا المورد."),
                        localized(responseLocale,
                                "The request was denied by the domain service. No business data was changed.",
                                "La demande a été refusée par le service métier. Aucune donnée n’a été modifiée.",
                                "رفضت خدمة المجال الطلب ولم تتغير أي بيانات أعمال."), null);
            }
            if (exception.getStatusCode().value() == 401) {
                log.error("A read-only assistant dependency rejected its internal authenticated call", exception);
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                        "A downstream ERP tool rejected the assistant call. Your IntelliOps session remains valid.", exception);
            }
            throw exception;
        }
        catch (Exception exception) {
            log.error("Conversational assistant request failed", exception);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "The NVIDIA agent or a downstream ERP service could not complete the request.", exception);
        }
        finally {
            intentGuard.clear();
        }
    }

    private void requireChatModel() {
        if (chatClient == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Conversational agent is unavailable. Configure NVIDIA_API_KEY and AGENT_LLM_PROVIDER=openai.");
        }
    }

    private String processPotentialToolCall(String answer, String userMessage, String locale) {
        if (answer == null) {
            return answer;
        }
        String narratedTool = narratedToolName(answer);
        if (narratedTool != null) {
            String toolResult = switch (narratedTool) {
                case "askBusinessQuestion" -> readOnlyAgentTools.askBusinessQuestion(userMessage);
                case "listProducts" -> readOnlyAgentTools.listProducts();
                default -> null;
            };
            if (toolResult != null) return formatToolResult(userMessage, toolResult, locale);
        }
        if (!answer.contains("{")) return answer;
        try {
            int start = answer.indexOf("{");
            int end = answer.lastIndexOf("}");
            if (start < 0 || end <= start) {
                return answer;
            }
            String jsonStr = answer.substring(start, end + 1);
            JsonNode node = objectMapper.readTree(jsonStr);

            String toolName = null;
            if (node.has("name")) {
                toolName = node.get("name").asText();
            } else if (node.has("operationId") || node.has("service")) {
                toolName = "executeOpenApiRead";
            }

            if (toolName == null) {
                return answer;
            }

            log.info("Intercepted pseudo tool call JSON from LLM: tool={}, json={}", toolName, jsonStr);
            String toolResult = null;
            JsonNode params = node.has("parameters") ? node.get("parameters") : node;

            switch (toolName) {
                case "listProducts" -> toolResult = readOnlyAgentTools.listProducts();
                case "listOpenApiReadOperations" -> toolResult = readOnlyAgentTools.listOpenApiReadOperations();
                case "executeOpenApiRead" -> {
                    String service = params.has("service") ? params.get("service").asText() : "";
                    String operationId = params.has("operationId") ? params.get("operationId").asText() : "";
                    Map<String, String> pathParams = parseMap(params, "pathParameters");
                    Map<String, String> queryParams = parseMap(params, "queryParameters");
                    toolResult = readOnlyAgentTools.executeOpenApiRead(service, operationId, pathParams, queryParams);
                }
                case "getInventory" -> {
                    Long storeId = longValue(params, "storeId", "idBoutique");
                    Long productId = longValue(params, "productId", "idProduit");
                    if (storeId == null || productId == null) return "Please provide both the store/location ID and product ID.";
                    toolResult = readOnlyAgentTools.getInventory(storeId, productId);
                }
                case "getLead" -> {
                    Long leadId = longValue(params, "leadId", "idLead");
                    if (leadId == null) return "Please provide the lead ID you want to inspect.";
                    toolResult = readOnlyAgentTools.getLead(leadId);
                }
                case "listAgentLeads" -> {
                    Long agentId = longValue(params, "agentId");
                    if (agentId == null) return "Please provide the CSM agent ID, or ask for your own assigned leads.";
                    toolResult = readOnlyAgentTools.listAgentLeads(agentId);
                }
                case "askBusinessQuestion" -> {
                    String q = params.has("question") ? params.get("question").asText() : userMessage;
                    toolResult = readOnlyAgentTools.askBusinessQuestion(q);
                }
                case "previewStockAdjustment" -> {
                    ApprovalService.ActionPreview preview = actionPreviewAgentTools.previewStockAdjustment(
                            longValue(params, "storeId", "idBoutique"),
                            longValue(params, "productId", "idProduit"),
                            intValue(params, "quantity", "quantite"),
                            textValue(params, "movementType", "typeMouvement"));
                    toolResult = objectMapper.writeValueAsString(preview);
                }
                case "previewLeadConversion" -> {
                    Long leadId = longValue(params, "leadId", "idLead");
                    Long locationId = longValue(params, "stockLocationId", "locationId");
                    List<org.example.mcpserver.tools.LeadMcpTools.ItemRequest> items = objectMapper.convertValue(
                            params.path("items"), objectMapper.getTypeFactory().constructCollectionType(
                                    List.class, org.example.mcpserver.tools.LeadMcpTools.ItemRequest.class));
                    toolResult = objectMapper.writeValueAsString(
                            actionPreviewAgentTools.previewLeadConversion(leadId, locationId, items));
                }
                case "listAvailableOperations" -> toolResult = actionPreviewAgentTools.listAvailableOperations();
                case "previewOpenApiMutation" -> toolResult = objectMapper.writeValueAsString(
                        actionPreviewAgentTools.previewOpenApiMutation(
                                textValue(params, "service"), textValue(params, "operationId"),
                                parseMap(params, "pathParameters"), parseMap(params, "queryParameters"),
                                textValue(params, "requestBodyJson")));
            }

            if (toolResult != null) {
                log.info("Successfully executed tool {} via fallback interceptor, result length={}", toolName, toolResult.length());
                return formatToolResult(userMessage, toolResult, locale);
            }
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (Exception e) {
            log.warn("Could not parse or execute intercepted tool call JSON: {}", e.getMessage());
        }

        return answer;
    }

    private Map<String, String> parseMap(JsonNode parent, String fieldName) {
        Map<String, String> result = new HashMap<>();
        if (parent.has(fieldName) && parent.get(fieldName).isObject()) {
            parent.get(fieldName).fields().forEachRemaining(entry ->
                    result.put(entry.getKey(), entry.getValue().asText()));
        }
        return result;
    }

    private String textValue(JsonNode node, String... names) {
        for (String name : names) {
            if (node.has(name)) return node.get(name).asText();
        }
        return "";
    }

    private Long longValue(JsonNode node, String... names) {
        for (String name : names) {
            if (node.has(name)) return node.get(name).asLong();
        }
        return null;
    }

    private int intValue(JsonNode node, String... names) {
        for (String name : names) {
            if (node.has(name)) return node.get(name).asInt();
        }
        return 0;
    }

    private boolean looksLikeRawToolCall(String answer) {
        if (answer == null) return false;
        String value = answer.trim();
        return value.startsWith("{") && (value.contains("\"name\"")
                || value.contains("\"parameters\"") || value.contains("\"operationId\""));
    }

    private String narratedToolName(String answer) {
        String normalized = answer.toLowerCase();
        if (normalized.contains("askbusinessquestion")) return "askBusinessQuestion";
        if (normalized.contains("listproducts")) return "listProducts";
        return null;
    }

    private String formatToolResult(String userMessage, String toolResult, String locale) {
        String formatPrompt = String.format("""
                The user asked: "%s"
                The trusted ERP backend returned:

                %s

                Answer the question using only this result. Use concise natural-language markdown.
                Respond in the language represented by locale "%s" (en=English, fr=French, ar=Arabic).
                When the result contains IDs for actionable resources, include those IDs in the answer so the
                user can safely identify an exact target in a later operation request.
                Never output JSON, tool names, function-call narration, or invented values.
                """, userMessage, toolResult, locale);
        String formatted = chatClient.prompt().user(formatPrompt).call().content();
        if (looksLikeRawToolCall(formatted) || narratedToolName(formatted) != null) {
            return safeBackendFallback(toolResult);
        }
        return formatted;
    }

    private String safeBackendFallback(String toolResult) {
        try {
            JsonNode result = objectMapper.readTree(toolResult);
            for (String field : List.of("answer", "summary", "message")) {
                if (result.hasNonNull(field) && result.get(field).isTextual()) {
                    return result.get(field).asText();
                }
            }
        } catch (Exception ignored) {
            // Never fall back to displaying untrusted raw JSON or a function-call transcript.
        }
        return "The ERP returned data, but the model could not safely format it. No values were invented and no change was made.";
    }

    private String normalizeLocale(String locale) {
        return locale != null && List.of("en", "fr", "ar").contains(locale) ? locale : "en";
    }

    private String localized(String locale, String english, String french, String arabic) {
        return switch (locale) {
            case "fr" -> french;
            case "ar" -> arabic;
            default -> english;
        };
    }

    private String localizeBlockedReason(String reason, String locale) {
        if ("en".equals(locale)) return reason;
        return localized(locale, reason,
                "La demande ne contient pas tous les identifiants ou paramètres requis pour préparer cette opération en toute sécurité.",
                "لا يحتوي الطلب على جميع المعرّفات أو المعلمات المطلوبة لتحضير العملية بأمان.");
    }

    private ApprovalService.ActionPreview localizePreview(
            ApprovalService.ActionPreview preview, String locale) {
        if (preview == null || "en".equals(locale)) return preview;
        String summary = preview.summary();
        var conversion = Pattern.compile(
                "Convert lead (\\d+) into an order with (\\d+) line\\(s\\) fulfilled by location (\\d+); catalog prices will be applied by the ERP")
                .matcher(summary);
        if (conversion.matches()) {
            summary = "fr".equals(locale)
                    ? "Convertir le prospect %s en commande avec %s ligne(s), préparée(s) depuis le lieu %s ; les prix du catalogue seront appliqués par l’ERP"
                            .formatted(conversion.group(1), conversion.group(2), conversion.group(3))
                    : "تحويل العميل المحتمل %s إلى طلب يضم %s بند، وتجهيزه من الموقع %s؛ وسيطبق نظام ERP أسعار الكتالوج"
                            .formatted(conversion.group(1), conversion.group(2), conversion.group(3));
        }
        return new ApprovalService.ActionPreview(
                preview.approvalToken(), preview.operation(), summary, preview.expiresAt(),
                preview.requiresExplicitConfirmation(), preview.riskLevel(), preview.requiresReason(),
                localized(locale, preview.nextStep(),
                        "Vérifiez cette opération, puis confirmez-la ou refusez-la explicitement.",
                        "راجع هذه العملية، ثم أكّدها أو ارفضها صراحةً."));
    }

    private String localizeDirect(String answer, String locale) {
        if ("en".equals(locale)) return answer;
        if (answer.startsWith("Hi!")) {
            return "fr".equals(locale)
                    ? "Bonjour ! Je peux consulter les données ERP, analyser les performances ou préparer une opération contrôlée. Que souhaitez-vous faire ?"
                    : "مرحباً! يمكنني الاطلاع على بيانات النظام وتحليل الأداء أو تحضير عملية خاضعة للموافقة. ماذا تريد أن تفعل؟";
        }
        if (answer.contains("three controlled modes")) {
            return "fr".equals(locale)
                    ? "**Je fonctionne selon trois modes contrôlés :** analyser les données réelles, produire des analyses BI et préparer des opérations soumises à votre approbation. Les droits du rôle et l’isolation de l’entreprise restent toujours appliqués."
                    : "**أعمل بثلاثة أنماط خاضعة للتحكم:** الاطلاع على البيانات الفعلية، إجراء تحليلات ذكاء الأعمال، وتحضير عمليات تتطلب موافقتك. تبقى صلاحيات الدور وعزل بيانات المؤسسة مطبقة دائماً.";
        }
        return answer;
    }

    public record AgentReply(String answer, String safety, ApprovalService.ActionPreview action) {
        public AgentReply(String answer, String safety) {
            this(answer, safety, null);
        }
    }
}
