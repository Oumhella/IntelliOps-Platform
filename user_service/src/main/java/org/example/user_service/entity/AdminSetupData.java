package org.example.user_service.entity;

import org.example.user_service.entity.Admin;
import org.example.user_service.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class AdminSetupData implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) throws Exception {
        String adminEmail = "abdellatifoum03@gmail.com";

        // Si l'admin n'existe pas déjà en base, on le crée proprement avec le PasswordEncoder de Spring
        if (userRepository.findByEmail(adminEmail).isEmpty()) {
            Admin admin = new Admin();
            admin.setEmail(adminEmail);
            // Hachage parfait et propre par Spring
            admin.setPassword(passwordEncoder.encode("Admin123!"));
            admin.setFirstname("Mohamed");
            admin.setLastname("Abdellatif");
            admin.setPhone("+212600000000");
            admin.setActive(true);
            admin.setEnterpriseId(1L);
            admin.setCreatedAt(LocalDateTime.now());

            userRepository.save(admin);
            System.out.println("🚀 [SUCCESS] Compte Admin créé automatiquement en BDD !");
        } else {
            System.out.println("ℹ️ [INFO] Le compte Admin existe déjà.");
        }
    }
}