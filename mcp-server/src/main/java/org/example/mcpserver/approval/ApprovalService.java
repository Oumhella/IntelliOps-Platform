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
        pendingActions.put(token, new PendingAction(operation, payload, expiresAt, currentCallerKey()));
        return new ActionPreview(token, operation, summary, expiresAt, true,
                "No change has been made. A human must explicitly confirm this token in a separate call.");
    }

    public <T> T confirm(String token, String expectedOperation, String confirmation, Class<T> type) {
        if (!"CONFIRM".equals(confirmation)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Confirmation must be exactly CONFIRM; no change was made.");
        }
        PendingAction action = pendingActions.get(token);
        if (action == null) {
            throw new ResponseStatusException(HttpStatus.GONE, "Approval token is missing, expired, or already used; no change was made.");
        }
        if (action.expiresAt().isBefore(Instant.now())) {
            pendingActions.remove(token, action);
            throw new ResponseStatusException(HttpStatus.GONE, "Approval token is missing, expired, or already used; no change was made.");
        }
        if (!MessageDigest.isEqual(action.callerKey(), currentCallerKey())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "This approval token belongs to another authenticated user; no change was made.");
        }
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
                                boolean requiresExplicitConfirmation, String nextStep) { }
    private record PendingAction(String operation, Object payload, Instant expiresAt, byte[] callerKey) { }

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
