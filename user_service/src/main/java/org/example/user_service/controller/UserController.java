package org.example.user_service.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.user_service.dto.request.*;
import org.example.user_service.dto.response.AuthResponse;
import org.example.user_service.dto.response.UserResponse;
import org.example.user_service.service.UserService;
import org.example.user_service.service.AuthenticationTokens;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.time.Duration;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {
    private static final String REFRESH_COOKIE = "intelliops_refresh";

    private final UserService userService;

    @Value("${app.auth.refresh-token-ttl:7d}")
    private Duration refreshTokenTtl;

    @Value("${app.auth.refresh-cookie-secure:true}")
    private boolean refreshCookieSecure;

    // ══════════════════════════════════════════════════════════════════
    //  PUBLIC ROUTES (no authentication required)
    // ══════════════════════════════════════════════════════════════════

    /**
     * Authenticate a user and return a JWT token.
     */
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest loginRequest) {
        AuthenticationTokens tokens = userService.login(loginRequest);
        return withRefreshCookie(tokens);
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(
            @CookieValue(name = REFRESH_COOKIE) String refreshToken) {
        return withRefreshCookie(userService.refresh(refreshToken));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorizationHeader,
            @CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken) {
        userService.logout(authorizationHeader, refreshToken);
        ResponseCookie expiredCookie = refreshCookie("").maxAge(Duration.ZERO).build();
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, expiredCookie.toString())
                .build();
    }

    /**
     * Register a new admin with their enterprise.
     */
    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
        UserResponse response = userService.register(request);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }
    @PostMapping("/password/forgot") public ResponseEntity<Map<String,String>> forgot(@Valid @RequestBody ForgotPasswordRequest request){userService.requestPasswordReset(request);return ResponseEntity.accepted().body(Map.of("message","If an active account exists, a reset link has been sent."));}
    @PostMapping("/password/reset") public ResponseEntity<Map<String,String>> reset(@Valid @RequestBody ResetPasswordRequest request){userService.resetPassword(request);return ResponseEntity.ok(Map.of("message","Password reset successfully."));}

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
    @GetMapping("/enterprise") @PreAuthorize("hasRole('ADMIN')") public ResponseEntity<org.example.user_service.dto.response.EnterpriseResponse> getEnterprise(@RequestAttribute("enterpriseId") Long id){return ResponseEntity.ok(userService.getEnterprise(id));}
    @PutMapping("/enterprise") @PreAuthorize("hasRole('ADMIN')") public ResponseEntity<org.example.user_service.dto.response.EnterpriseResponse> updateEnterprise(@RequestAttribute("enterpriseId") Long id,@Valid @RequestBody EnterpriseUpdateRequest request){return ResponseEntity.ok(userService.updateEnterprise(id,request));}

    // ══════════════════════════════════════════════════════════════════
    //  STAFF MANAGEMENT ROUTES (admin operations)
    // ══════════════════════════════════════════════════════════════════

    /**
     * Create a new staff member (CSM, logistics agent, or internal courier).
     * Only accessible by users with ADMIN role.
     */
    @PostMapping("/staff")
    @PreAuthorize("hasRole('ADMIN')")
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
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<UserResponse>> getEnterpriseStaff(
            @RequestAttribute("enterpriseId") Long enterpriseId
    ) {
        List<UserResponse> staffList = userService.getUsersByEnterprise(enterpriseId);
        return ResponseEntity.ok(staffList);
    }

    /**
     * List active internal couriers available for delivery assignment.
     */
    @GetMapping("/staff/couriers")
    @PreAuthorize("hasAnyRole('ADMIN', 'LOGISTIC')")
    public ResponseEntity<List<UserResponse>> getActiveCouriers(
            @RequestAttribute("enterpriseId") Long enterpriseId
    ) {
        return ResponseEntity.ok(userService.getActiveCouriersByEnterprise(enterpriseId));
    }

    @GetMapping("/staff/couriers/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'LOGISTIC')")
    public ResponseEntity<UserResponse> getActiveCourier(
            @PathVariable Long id,
            @RequestAttribute("enterpriseId") Long enterpriseId
    ) {
        return ResponseEntity.ok(userService.getActiveCourier(id, enterpriseId));
    }

    /**
     * Get a single staff member by ID (within the same enterprise).
     */
    @GetMapping("/staff/{id}")
    @PreAuthorize("hasRole('ADMIN')")
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
    @PreAuthorize("hasRole('ADMIN')")
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
     * Deactivate a staff member while preserving historical ownership/audit references.
     * Only accessible by users with ADMIN role.
     */
    @DeleteMapping("/staff/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteStaffMember(
            @PathVariable Long id,
            @RequestAttribute("enterpriseId") Long enterpriseId
    ) {
        userService.deleteUser(id, enterpriseId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Get a user by ID. Primarily used for inter-service communication.
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserResponse> getUserById(
            @PathVariable Long id,
            @RequestAttribute("enterpriseId") Long enterpriseId) {
        UserResponse response = userService.getUserById(id, enterpriseId);
        return ResponseEntity.ok(response);
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

    private ResponseEntity<AuthResponse> withRefreshCookie(AuthenticationTokens tokens) {
        ResponseCookie cookie = refreshCookie(tokens.refreshToken())
                .maxAge(refreshTokenTtl)
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(tokens.response());
    }

    private ResponseCookie.ResponseCookieBuilder refreshCookie(String value) {
        return ResponseCookie.from(REFRESH_COOKIE, value)
                .httpOnly(true)
                .secure(refreshCookieSecure)
                .sameSite("Strict")
                .path("/api/v1/users");
    }
}

