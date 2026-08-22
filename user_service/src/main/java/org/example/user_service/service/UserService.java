package org.example.user_service.service;

import org.example.user_service.dto.request.*;
import org.example.user_service.dto.response.AuthResponse;
import org.example.user_service.dto.response.UserResponse;
import org.example.user_service.dto.response.EnterpriseResponse;

import java.util.List;

public interface UserService {

    UserResponse register(RegisterRequest request);

    AuthenticationTokens login(LoginRequest request);

    AuthenticationTokens refresh(String refreshToken);

    void logout(String authorizationHeader, String refreshToken);

    UserResponse createUser(UserCreationRequest userCreationRequest, Long enterpriseId);

    List<UserResponse> getUsersByEnterprise(Long enterpriseId);

    List<UserResponse> getActiveCouriersByEnterprise(Long enterpriseId);

    UserResponse getActiveCourier(Long userId, Long enterpriseId);

    // ── Profile operations ──────────────────────────────────────────

    UserResponse getProfile(String email);

    UserResponse updateProfile(String email, ProfileUpdateRequest request);

    void changePassword(String email, ChangePasswordRequest request);
    void requestPasswordReset(ForgotPasswordRequest request);
    void resetPassword(ResetPasswordRequest request);
    EnterpriseResponse getEnterprise(Long enterpriseId);
    EnterpriseResponse updateEnterprise(Long enterpriseId, EnterpriseUpdateRequest request);

    // ── Staff management operations ─────────────────────────────────

    UserResponse getStaffMember(Long userId, Long enterpriseId);

    UserResponse toggleUserStatus(Long userId, Long enterpriseId, boolean active);

    void deleteUser(Long userId, Long enterpriseId);

    UserResponse getUserById(Long userId, Long enterpriseId);
}

