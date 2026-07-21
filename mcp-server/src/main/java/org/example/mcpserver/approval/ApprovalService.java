package org.example.mcpserver.approval;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
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
        String token = UUID.randomUUID().toString();
        Instant expiresAt = Instant.now().plusSeconds(ttlSeconds);
        pendingActions.put(token, new PendingAction(operation, payload, expiresAt));
        return new ActionPreview(token, operation, summary, expiresAt, true,
                "No change has been made. A human must explicitly confirm this token in a separate call.");
    }

    public <T> T confirm(String token, String expectedOperation, String confirmation, Class<T> type) {
        if (!"CONFIRM".equals(confirmation)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Confirmation must be exactly CONFIRM; no change was made.");
        }
        PendingAction action = pendingActions.remove(token);
        if (action == null || action.expiresAt().isBefore(Instant.now())) {
            throw new ResponseStatusException(HttpStatus.GONE, "Approval token is missing, expired, or already used; no change was made.");
        }
        if (!expectedOperation.equals(action.operation()) || !type.isInstance(action.payload())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Approval token does not match this operation; no change was made.");
        }
        return type.cast(action.payload());
    }

    public record ActionPreview(String approvalToken, String operation, String summary, Instant expiresAt,
                                boolean requiresExplicitConfirmation, String nextStep) { }
    private record PendingAction(String operation, Object payload, Instant expiresAt) { }
}
