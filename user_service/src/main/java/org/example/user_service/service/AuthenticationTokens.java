package org.example.user_service.service;

import org.example.user_service.dto.response.AuthResponse;

public record AuthenticationTokens(AuthResponse response, String refreshToken) {
}
