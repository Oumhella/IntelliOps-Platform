package org.example.user_service.controller;

import lombok.RequiredArgsConstructor;
import org.example.user_service.dto.response.PlatformOverviewResponse;
import org.example.user_service.service.PlatformOverviewService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/platform")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class PlatformController {

    private final PlatformOverviewService platformOverviewService;

    @GetMapping("/overview")
    public PlatformOverviewResponse getOverview() {
        return platformOverviewService.getOverview();
    }

    @GetMapping("/audit")
    public List<PlatformOverviewResponse.PlatformEvent> getAudit() {
        return platformOverviewService.getAudit();
    }

    @GetMapping("/settings")
    public PlatformOverviewResponse.PlatformSettings getSettings() {
        return platformOverviewService.getSettings();
    }
}
