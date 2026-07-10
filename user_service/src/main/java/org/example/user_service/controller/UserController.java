package org.example.user_service.controller;

import lombok.RequiredArgsConstructor;
import org.example.user_service.dto.request.LoginRequest;
import org.example.user_service.dto.request.UserCreationRequest;
import org.example.user_service.dto.response.AuthResponse;
import org.example.user_service.dto.response.UserResponse;
import org.example.user_service.service.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;

    /**
     * 1. Route publique : Authentification d'un utilisateur
     */
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody LoginRequest loginRequest) {
        AuthResponse response = userService.login(loginRequest);
        return ResponseEntity.ok(response);
    }

    /**
     * ROUTE TEMPORAIRE : Permet de créer le premier administrateur système sans token.
     * Tu pourras supprimer ou commenter cette méthode une fois le premier admin créé !
     */
    @PostMapping("/setup-admin")
    public ResponseEntity<UserResponse> setupFirstAdmin(@RequestBody UserCreationRequest creationRequest) {
        // On force l'enterprise_id à 1 pour ce premier admin global
        UserResponse response = userService.createUser(creationRequest, 1L);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }
    /**
     * 2. Route protégée : Création d'un agent (CSM ou Logistique) par l'Admin de l'entreprise.
     * Seul un utilisateur avec le rôle ADMIN peut appeler cette route.
     */
    @PostMapping("/staff")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public ResponseEntity<UserResponse> createStaffMember(
            @RequestBody UserCreationRequest creationRequest,
            @RequestAttribute("enterpriseId") Long enterpriseId // Récupéré depuis le filtre JWT
    ) {
        UserResponse response = userService.createUser(creationRequest, enterpriseId);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }
    /**
     * 3. Route protégée : Récupérer tous les collaborateurs de l'entreprise de l'utilisateur connecté.
     * Accessible par l'Admin ou les agents pour la visibilité d'équipe.
     */
    @GetMapping("/staff")
    public ResponseEntity<List<UserResponse>> getEnterpriseStaff(
            @RequestAttribute("enterpriseId") Long enterpriseId // Récupéré depuis le filtre JWT
    ) {
        List<UserResponse> staffList = userService.getUsersByEnterprise(enterpriseId);
        return ResponseEntity.ok(staffList);
    }
    /**
     * 4. Route optionnelle : Connaître les détails de l'utilisateur actuellement connecté
     */
    @GetMapping("/me")
    public ResponseEntity<String> getConnectedUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String currentPrincipalName = authentication.getName(); // Retourne l'email
        return ResponseEntity.ok("Utilisateur connecté : " + currentPrincipalName);
    }
}
