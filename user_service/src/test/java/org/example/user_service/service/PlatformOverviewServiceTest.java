package org.example.user_service.service;

import org.example.user_service.dto.response.PlatformOverviewResponse;
import org.example.user_service.entity.Admin;
import org.example.user_service.repository.AdminRepository;
import org.example.user_service.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlatformOverviewServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private AdminRepository adminRepository;

    @Mock
    private DiscoveryClient discoveryClient;

    @InjectMocks
    private PlatformOverviewService platformOverviewService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(
                platformOverviewService,
                "expectedServices",
                "user-service,stock-service"
        );
    }

    @Test
    void buildsOverviewFromDatabaseAndServiceRegistry() {
        Admin admin = new Admin();
        admin.setEnterpriseId(42L);
        admin.setCompanyName("Northstar Labs");
        admin.setActivityType("Retail");
        admin.setActive(true);
        admin.setCreatedAt(LocalDateTime.of(2026, 8, 1, 10, 0));

        when(adminRepository.findAllByEnterpriseIdNotOrderByCreatedAtDesc(0L)).thenReturn(List.of(admin));
        when(userRepository.countByEnterpriseId(42L)).thenReturn(4L);
        when(userRepository.countBusinessEnterprises()).thenReturn(1L);
        when(userRepository.countBusinessUsers()).thenReturn(4L);
        when(userRepository.countActiveBusinessUsers()).thenReturn(3L);
        when(discoveryClient.getServices()).thenReturn(List.of("USER-SERVICE"));
        when(discoveryClient.getInstances("USER-SERVICE")).thenReturn(List.of(mock(ServiceInstance.class)));

        PlatformOverviewResponse overview = platformOverviewService.getOverview();

        assertThat(overview.totals().enterprises()).isEqualTo(1);
        assertThat(overview.totals().users()).isEqualTo(4);
        assertThat(overview.totals().activeUsers()).isEqualTo(3);
        assertThat(overview.totals().onlineServices()).isEqualTo(1);
        assertThat(overview.totals().totalServices()).isEqualTo(2);
        assertThat(overview.tenants()).singleElement().satisfies(tenant -> {
            assertThat(tenant.companyName()).isEqualTo("Northstar Labs");
            assertThat(tenant.userCount()).isEqualTo(4);
        });
        assertThat(overview.services()).extracting(PlatformOverviewResponse.ServiceSummary::status)
                .containsExactly("ONLINE", "OFFLINE");
    }
}
