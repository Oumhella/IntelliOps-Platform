package org.example.user_service.service;

import org.example.user_service.dto.request.LoginRequest;
import org.example.user_service.dto.request.UserCreationRequest;
import org.example.user_service.dto.response.AuthResponse;
import org.example.user_service.dto.response.UserResponse;

import java.util.List;

public interface UserService {

    AuthResponse login(LoginRequest request);

    UserResponse createUser(UserCreationRequest userCreationRequest, Long enterpriseId);

    List<UserResponse> getUsersByEnterprise(Long enterpriseId);
}
