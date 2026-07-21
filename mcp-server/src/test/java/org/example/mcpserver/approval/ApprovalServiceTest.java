package org.example.mcpserver.approval;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ApprovalServiceTest {
    @Test
    void onlyAnExplicitSecondConfirmationCanConsumeThePreview() {
        ApprovalService service = new ApprovalService(300);
        ApprovalService.ActionPreview preview = service.prepare("TEST", "payload", "A safe preview");

        assertThrows(ResponseStatusException.class,
                () -> service.confirm(preview.approvalToken(), "TEST", "yes", String.class));
        assertEquals("payload", service.confirm(preview.approvalToken(), "TEST", "CONFIRM", String.class));
        assertThrows(ResponseStatusException.class,
                () -> service.confirm(preview.approvalToken(), "TEST", "CONFIRM", String.class));
    }
}
