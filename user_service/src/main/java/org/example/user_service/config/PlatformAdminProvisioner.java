package org.example.user_service.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.user_service.entity.SuperAdmin;
import org.example.user_service.entity.User;
import org.example.user_service.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Component
@RequiredArgsConstructor
@Slf4j
public class PlatformAdminProvisioner implements ApplicationRunner {

    private static final Long PLATFORM_ENTERPRISE_ID = 0L;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${platform.admin.email:}")
    private String email;

    @Value("${platform.admin.password:}")
    private String password;

    @Value("${platform.admin.firstname:Platform}")
    private String firstname;

    @Value("${platform.admin.lastname:Administrator}")
    private String lastname;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        boolean emailConfigured = email != null && !email.isBlank();
        boolean passwordConfigured = password != null && !password.isBlank();

        if (!emailConfigured && !passwordConfigured) {
            log.info("Platform administrator provisioning is disabled");
            return;
        }
        if (!emailConfigured || !passwordConfigured) {
            throw new IllegalStateException("Both PLATFORM_ADMIN_EMAIL and PLATFORM_ADMIN_PASSWORD must be configured");
        }
        if (password.length() < 12) {
            throw new IllegalStateException("PLATFORM_ADMIN_PASSWORD must contain at least 12 characters");
        }

        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        User existingUser = userRepository.findByEmail(normalizedEmail).orElse(null);
        if (existingUser != null) {
            if (!(existingUser instanceof SuperAdmin)) {
                throw new IllegalStateException("The configured platform administrator email belongs to a business user");
            }
            return;
        }

        SuperAdmin platformAdmin = new SuperAdmin();
        platformAdmin.setEmail(normalizedEmail);
        platformAdmin.setPassword(passwordEncoder.encode(password));
        platformAdmin.setFirstname(firstname);
        platformAdmin.setLastname(lastname);
        platformAdmin.setEnterpriseId(PLATFORM_ENTERPRISE_ID);
        platformAdmin.setActive(true);
        userRepository.save(platformAdmin);
        log.info("Provisioned platform administrator account for {}", platformAdmin.getEmail());
    }
}
