package org.example.mcpserver.approval;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApprovalServiceTest {
    @Test
    void onlyAnExplicitSecondConfirmationCanConsumeThePreview() {
        authenticateAs("owner-token");
        ApprovalService service = new ApprovalService(300);
        ApprovalService.ActionPreview preview = service.prepare("TEST", "payload", "A safe preview");

        assertThrows(ResponseStatusException.class,
                () -> service.confirm(preview.approvalToken(), "TEST", "yes", String.class));
        assertEquals("payload", service.confirm(preview.approvalToken(), "TEST", "CONFIRM", String.class));
        assertThrows(ResponseStatusException.class,
                () -> service.confirm(preview.approvalToken(), "TEST", "CONFIRM", String.class));
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void anotherAuthenticatedCallerCannotConsumeThePreview() {
        ApprovalService service = new ApprovalService(300);
        authenticateAs("owner-token");
        ApprovalService.ActionPreview preview = service.prepare("TEST", "payload", "A safe preview");

        authenticateAs("attacker-token");
        assertThrows(ResponseStatusException.class,
                () -> service.confirm(preview.approvalToken(), "TEST", "CONFIRM", String.class));

        authenticateAs("owner-token");
        assertEquals("payload", service.confirm(preview.approvalToken(), "TEST", "CONFIRM", String.class));
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void exposesOnlyTheCurrentCallersLatestPreviewAndAllowsExplicitRejection() {
        authenticateAs("owner-token");
        ApprovalService service = new ApprovalService(300);
        var since = java.time.Instant.now().minusSeconds(1);
        ApprovalService.ActionPreview preview = service.prepare("TEST", "payload", "Review this exact action");

        assertEquals(preview.approvalToken(), service.latestForCurrentCallerSince(since).orElseThrow().approvalToken());
        assertEquals("Review this exact action", service.reject(preview.approvalToken()).summary());
        assertTrue(service.latestForCurrentCallerSince(since).isEmpty());
        assertThrows(ResponseStatusException.class,
                () -> service.confirm(preview.approvalToken(), "TEST", "CONFIRM", String.class));
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void highRiskActionRequiresStrongConfirmationAndABusinessReason() {
        authenticateAs("owner-token");
        ApprovalService service = new ApprovalService(300);
        ApprovalService.ActionPreview preview = service.prepare("REFUND", "payload", "Refund payment 42",
                ApprovalService.RiskLevel.HIGH, true);

        assertThrows(ResponseStatusException.class,
                () -> service.confirm(preview.approvalToken(), "REFUND", "CONFIRM", "Duplicate", String.class));
        assertThrows(ResponseStatusException.class,
                () -> service.confirm(preview.approvalToken(), "REFUND", "CONFIRM HIGH RISK", "short", String.class));
        assertEquals("payload", service.confirm(preview.approvalToken(), "REFUND", "CONFIRM HIGH RISK",
                "Duplicate payment recorded", String.class));
        RequestContextHolder.resetRequestAttributes();
    }

    private void authenticateAs(String token) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }
}
