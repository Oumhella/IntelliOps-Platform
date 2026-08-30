package org.example.mcpserver.approval;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Comparator;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Keeps destructive MCP actions pending until a separate, explicit confirmation call. */
@Service
public class ApprovalService {
    private final ConcurrentHashMap<String, PendingAction> pendingActions = new ConcurrentHashMap<>();
    private final long ttlSeconds;

    public ApprovalService(@Value("${mcp.approval.ttl-seconds:300}") long ttlSeconds) {
        this.ttlSeconds = ttlSeconds;
    }

    public ActionPreview prepare(String operation, Object payload, String summary) {
        return prepare(operation, payload, summary, RiskLevel.MEDIUM, false);
    }

    public ActionPreview prepare(String operation, Object payload, String summary,
                                 RiskLevel riskLevel, boolean requiresReason) {
        String token = UUID.randomUUID().toString();
        Instant createdAt = Instant.now();
        Instant expiresAt = createdAt.plusSeconds(ttlSeconds);
        pendingActions.put(token, new PendingAction(operation, payload, summary, riskLevel, requiresReason,
                createdAt, expiresAt, currentCallerKey()));
        return new ActionPreview(token, operation, summary, expiresAt, true, riskLevel, requiresReason,
                "No change has been made. A human must explicitly confirm this token in a separate call.");
    }

    public Optional<ActionPreview> latestForCurrentCallerSince(Instant since) {
        byte[] callerKey = currentCallerKey();
        Instant now = Instant.now();
        return pendingActions.entrySet().stream()
                .filter(entry -> MessageDigest.isEqual(entry.getValue().callerKey(), callerKey))
                .filter(entry -> !entry.getValue().createdAt().isBefore(since))
                .filter(entry -> entry.getValue().expiresAt().isAfter(now))
                .max(Comparator.comparing(entry -> entry.getValue().createdAt()))
                .map(entry -> toPreview(entry.getKey(), entry.getValue()));
    }

    public String operationFor(String token) {
        return requireOwnedPendingAction(token).operation();
    }

    public ActionPreview reject(String token) {
        PendingAction action = requireOwnedPendingAction(token);
        if (!pendingActions.remove(token, action)) {
            throw expired();
        }
        return toPreview(token, action);
    }

    public <T> T confirm(String token, String expectedOperation, String confirmation, Class<T> type) {
        return confirm(token, expectedOperation, confirmation, null, type);
    }

    public <T> T confirm(String token, String expectedOperation, String confirmation, String reason, Class<T> type) {
        PendingAction action = requireOwnedPendingAction(token);
        validateConfirmation(action, confirmation, reason);
        if (!expectedOperation.equals(action.operation()) || !type.isInstance(action.payload())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Approval token does not match this operation; no change was made.");
        }
        if (!pendingActions.remove(token, action)) {
            throw new ResponseStatusException(HttpStatus.GONE,
                    "Approval token was already used; no change was made.");
        }
        return type.cast(action.payload());
    }

    public record ActionPreview(String approvalToken, String operation, String summary, Instant expiresAt,
                                boolean requiresExplicitConfirmation, RiskLevel riskLevel,
                                boolean requiresReason, String nextStep) { }
    public enum RiskLevel { LOW, MEDIUM, HIGH }
    private record PendingAction(String operation, Object payload, String summary, RiskLevel riskLevel,
                                 boolean requiresReason, Instant createdAt, Instant expiresAt,
                                 byte[] callerKey) { }

    private PendingAction requireOwnedPendingAction(String token) {
        PendingAction action = pendingActions.get(token);
        if (action == null) {
            throw expired();
        }
        if (action.expiresAt().isBefore(Instant.now())) {
            pendingActions.remove(token, action);
            throw expired();
        }
        if (!MessageDigest.isEqual(action.callerKey(), currentCallerKey())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "This approval token belongs to another authenticated user; no change was made.");
        }
        return action;
    }

    private ActionPreview toPreview(String token, PendingAction action) {
        return new ActionPreview(token, action.operation(), action.summary(), action.expiresAt(), true,
                action.riskLevel(), action.requiresReason(),
                "Review this action, then explicitly confirm or reject it.");
    }

    private void validateConfirmation(PendingAction action, String confirmation, String reason) {
        String requiredText = action.riskLevel() == RiskLevel.HIGH ? "CONFIRM HIGH RISK" : "CONFIRM";
        if (!requiredText.equals(confirmation)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Confirmation must be exactly " + requiredText + "; no change was made.");
        }
        if (action.requiresReason() && (reason == null || reason.trim().length() < 10)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "A business reason of at least 10 characters is required; no change was made.");
        }
    }

    private ResponseStatusException expired() {
        return new ResponseStatusException(HttpStatus.GONE,
                "Approval token is missing, expired, or already used; no change was made.");
    }

    private byte[] currentCallerKey() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "An authenticated HTTP request is required for approval actions.");
        }
        HttpServletRequest request = attributes.getRequest();
        String authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "A bearer token is required for approval actions.");
        }
        try {
            return MessageDigest.getInstance("SHA-256")
                    .digest(authorization.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }
}
