package org.example.mcpserver.approval;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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

    private void authenticateAs(String token) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }
}
