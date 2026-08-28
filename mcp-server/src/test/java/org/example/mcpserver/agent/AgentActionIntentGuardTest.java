package org.example.mcpserver.agent;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AgentActionIntentGuardTest {
    private final AgentActionIntentGuard guard = new AgentActionIntentGuard();

    @AfterEach
    void clear() {
        guard.clear();
    }

    @Test
    void capabilityQuestionCanNeverBecomeAMutationPreview() {
        guard.begin("What can you do?");
        assertThrows(ResponseStatusException.class,
                () -> guard.requireGenericMutation("rembourser", Map.of("paymentId", "12345")));
    }

    @Test
    void blocksAnIdentifierInventedByTheModel() {
        guard.begin("Refund payment 42 because it was duplicated");
        assertThrows(ResponseStatusException.class,
                () -> guard.requireGenericMutation("rembourser", Map.of("paymentId", "12345")));
    }

    @Test
    void acceptsAnExplicitRelevantActionWithTheExactUserSuppliedTarget() {
        guard.begin("Refund payment 42 because it was duplicated");
        assertDoesNotThrow(() -> guard.requireGenericMutation("rembourser", Map.of("paymentId", "42")));
    }

    @Test
    void recognizesFrenchPreparationAndConversionNounsAsWriteIntent() {
        guard.begin("Préparer la conversion du prospect 2 avec le produit 8 au lieu 1");
        assertDoesNotThrow(guard::requireExplicitWrite);
    }
}
