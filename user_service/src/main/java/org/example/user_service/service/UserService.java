package org.example.user_service.service;

import org.example.user_service.dto.request.*;
import org.example.user_service.dto.response.AuthResponse;
import org.example.user_service.dto.response.UserResponse;

import java.util.List;

public interface UserService {

    UserResponse register(RegisterRequest request);

    AuthResponse login(LoginRequest request);

    UserResponse createUser(UserCreationRequest userCreationRequest, Long enterpriseId);

    List<UserResponse> getUsersByEnterprise(Long enterpriseId);

    // ── Profile operations ──────────────────────────────────────────

    UserResponse getProfile(String email);

    UserResponse updateProfile(String email, ProfileUpdateRequest request);

    void changePassword(String email, ChangePasswordRequest request);

    // ── Staff management operations ─────────────────────────────────

    UserResponse getStaffMember(Long userId, Long enterpriseId);

    UserResponse toggleUserStatus(Long userId, Long enterpriseId, boolean active);

    void deleteUser(Long userId, Long enterpriseId);

    UserResponse getUserById(Long userId);
}

