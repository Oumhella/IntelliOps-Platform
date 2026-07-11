package org.example.user_service.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.user_service.dto.request.*;
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
import java.util.Map;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;

    // ══════════════════════════════════════════════════════════════════
    //  PUBLIC ROUTES (no authentication required)
    // ══════════════════════════════════════════════════════════════════

    /**
     * Authenticate a user and return a JWT token.
     */
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest loginRequest) {
        AuthResponse response = userService.login(loginRequest);
        return ResponseEntity.ok(response);
    }

    /**
     * Register a new admin with their enterprise.
     */
    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
        UserResponse response = userService.register(request);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    /**
     * TEMPORARY ROUTE: Create the first system admin without a token.
     * Can be removed or disabled once the first admin is created.
     */
    @PostMapping("/setup-admin")
    public ResponseEntity<UserResponse> setupFirstAdmin(@Valid @RequestBody UserCreationRequest creationRequest) {
        UserResponse response = userService.createUser(creationRequest, 1L);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    // ══════════════════════════════════════════════════════════════════
    //  PROFILE ROUTES (any authenticated user)
    // ══════════════════════════════════════════════════════════════════

    /**
     * Get the full profile of the currently authenticated user.
     */
    @GetMapping("/me")
    public ResponseEntity<UserResponse> getMyProfile() {
        String email = getCurrentUserEmail();
        UserResponse response = userService.getProfile(email);
        return ResponseEntity.ok(response);
    }

    /**
     * Update the profile of the currently authenticated user (partial update).
     * Only non-null fields in the request body will be updated.
     */
    @PutMapping("/me")
    public ResponseEntity<UserResponse> updateMyProfile(@Valid @RequestBody ProfileUpdateRequest request) {
        String email = getCurrentUserEmail();
        UserResponse response = userService.updateProfile(email, request);
        return ResponseEntity.ok(response);
    }

    /**
     * Change the password of the currently authenticated user.
     * Requires the current password for verification.
     */
    @PutMapping("/me/password")
    public ResponseEntity<Map<String, String>> changeMyPassword(
            @Valid @RequestBody ChangePasswordRequest request) {
        String email = getCurrentUserEmail();
        userService.changePassword(email, request);
        return ResponseEntity.ok(Map.of("message", "Password changed successfully"));
    }

    // ══════════════════════════════════════════════════════════════════
    //  STAFF MANAGEMENT ROUTES (admin operations)
    // ══════════════════════════════════════════════════════════════════

    /**
     * Create a new staff member (CSM or Logistic agent).
     * Only accessible by users with ADMIN role.
     */
    @PostMapping("/staff")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public ResponseEntity<UserResponse> createStaffMember(
            @Valid @RequestBody UserCreationRequest creationRequest,
            @RequestAttribute("enterpriseId") Long enterpriseId
    ) {
        UserResponse response = userService.createUser(creationRequest, enterpriseId);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    /**
     * Get all staff members belonging to the authenticated user's enterprise.
     */
    @GetMapping("/staff")
    public ResponseEntity<List<UserResponse>> getEnterpriseStaff(
            @RequestAttribute("enterpriseId") Long enterpriseId
    ) {
        List<UserResponse> staffList = userService.getUsersByEnterprise(enterpriseId);
        return ResponseEntity.ok(staffList);
    }

    /**
     * Get a single staff member by ID (within the same enterprise).
     */
    @GetMapping("/staff/{id}")
    public ResponseEntity<UserResponse> getStaffMember(
            @PathVariable Long id,
            @RequestAttribute("enterpriseId") Long enterpriseId
    ) {
        UserResponse response = userService.getStaffMember(id, enterpriseId);
        return ResponseEntity.ok(response);
    }

    /**
     * Activate or deactivate a staff member's account.
     * Only accessible by users with ADMIN role.
     * Request body: { "active": true/false }
     */
    @PatchMapping("/staff/{id}/status")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public ResponseEntity<UserResponse> toggleStaffStatus(
            @PathVariable Long id,
            @RequestAttribute("enterpriseId") Long enterpriseId,
            @RequestBody Map<String, Boolean> body
    ) {
        boolean active = body.getOrDefault("active", true);
        UserResponse response = userService.toggleUserStatus(id, enterpriseId, active);
        return ResponseEntity.ok(response);
    }

    /**
     * Permanently delete a staff member from the database.
     * Only accessible by users with ADMIN role.
     */
    @DeleteMapping("/staff/{id}")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public ResponseEntity<Void> deleteStaffMember(
            @PathVariable Long id,
            @RequestAttribute("enterpriseId") Long enterpriseId
    ) {
        userService.deleteUser(id, enterpriseId);
        return ResponseEntity.noContent().build();
    }

    // ══════════════════════════════════════════════════════════════════
    //  PRIVATE HELPERS
    // ══════════════════════════════════════════════════════════════════

    /**
     * Extract the email of the currently authenticated user from the SecurityContext.
     */
    private String getCurrentUserEmail() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication.getName();
    }
}

