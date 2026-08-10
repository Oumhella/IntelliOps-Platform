package org.example.user_service.service;

import lombok.RequiredArgsConstructor;
import org.example.user_service.config.UserJwtGenerator;
import org.example.user_service.dto.request.ChangePasswordRequest;
import org.example.user_service.dto.request.ProfileUpdateRequest;
import org.example.user_service.dto.request.RegisterRequest;
import org.example.user_service.dto.request.UserCreationRequest;
import org.example.user_service.dto.response.UserResponse;
import org.example.user_service.entity.*;
import org.example.common.exception.ConflictException;
import org.example.common.exception.ResourceNotFoundException;
import org.example.common.exception.UnauthorizedException;
import org.example.user_service.mapper.UserMapper;
import org.example.user_service.repository.RefreshTokenRedisRepository;
import org.example.user_service.repository.UserRepository;
import org.example.user_service.repository.EnterpriseRepository;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.example.user_service.dto.response.AuthResponse;
import org.example.user_service.dto.request.LoginRequest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final EnterpriseRepository enterpriseRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserMapper userMapper;
    private final UserJwtGenerator userJwtGenerator;
    private final RefreshTokenRedisRepository refreshTokenRepository;
    private final StringRedisTemplate redisTemplate; // Utile pour la blacklist des Access Tokens
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final String BLACKLIST_PREFIX = "jwt:blacklist:";

    @Value("${app.auth.refresh-token-ttl:7d}")
    private Duration refreshTokenTtl;
    // ── Authentication ──────────────────────────────────────────────

    @Override
    @Transactional
    public UserResponse register(RegisterRequest request) {
        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new ConflictException("This email is already in use");
        }

        Enterprise enterprise = new Enterprise();
        enterprise.setCompanyName(request.getCompanyName().trim());
        enterprise.setActivityType(request.getActivityType().trim());
        enterprise.setActive(true);
        Long newEnterpriseId = enterpriseRepository.save(enterprise).getId();

        Admin admin = userMapper.toAdminEntity(request, newEnterpriseId);
        admin.setCompanyName(enterprise.getCompanyName());
        admin.setActivityType(enterprise.getActivityType());
        admin.setPassword(passwordEncoder.encode(admin.getPassword()));
        // createdAt is now set automatically via @PrePersist

        User savedAdmin = userRepository.save(admin);
        return userMapper.toResponse(savedAdmin);
    }

    @Override
    public AuthenticationTokens login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new UnauthorizedException("Invalid credentials"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new UnauthorizedException("Invalid credentials");
        }

        if (!user.isActive()) {
            throw new UnauthorizedException("This account is deactivated");
        }

        if (user.getRole() != Role.ROLE_SUPER_ADMIN) {
            enterpriseRepository.findById(user.getEnterpriseId()).ifPresent(enterprise -> {
                if (!enterprise.isActive()) {
                    throw new UnauthorizedException("This enterprise workspace is deactivated");
                }
            });
        }

        String realToken = userJwtGenerator.generateToken(user);

        AuthResponse response = new AuthResponse(
                realToken,
                user.getId(),
                user.getEmail(),
                user.getFirstname(),
                user.getLastname(),
                user.getRole().name(),
                user.getEnterpriseId()
        );
        return new AuthenticationTokens(response, createRefreshToken(user.getId()));
    }

    private String createRefreshToken(Long userId) {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        refreshTokenRepository.save(new RefreshTokenRedis(
                hashRefreshToken(rawToken), userId, refreshTokenTtl.toSeconds()));
        return rawToken;
    }
    // 2. Traitement de la requête /refresh
    @Override
    public AuthenticationTokens refresh(String refreshToken) {
        String refreshTokenHash = hashRefreshToken(refreshToken);
        if (!refreshTokenRepository.existsById(refreshTokenHash)) {
            throw new UnauthorizedException("Invalid or expired refresh token");
        }
        RefreshTokenRedis storedToken = refreshTokenRepository.findById(refreshTokenHash)
                .orElseThrow(() -> new RuntimeException("Refresh token invalide ou expiré"));

        User user = userRepository.findById(storedToken.getUserId())
                .orElseThrow(() -> new UnauthorizedException("Invalid or expired refresh token"));
        if (!user.isActive()) {
            throw new UnauthorizedException("This account is deactivated");
        }

        // Rotation du token (Sécurité : supprimer l'ancien et créer un nouveau)
        refreshTokenRepository.deleteById(storedToken.getId());
        String newRefreshToken = createRefreshToken(user.getId());
        String newAccessToken = userJwtGenerator.generateToken(user);

        AuthResponse response = new AuthResponse(
                newAccessToken, user.getId(), user.getEmail(), user.getFirstname(),
                user.getLastname(), user.getRole().name(), user.getEnterpriseId());
        return new AuthenticationTokens(response, newRefreshToken);
    }

    // 3. Traitement de la déconnexion (Logout)
    @Override
    public void logout(String accessToken, String refreshToken) {
        // A. Revocation du Refresh Token dans Redis
        if (refreshToken != null && !refreshToken.isBlank()) {
            refreshTokenRepository.deleteById(hashRefreshToken(refreshToken));
        }

        // B. Ajout de l'Access Token à la Blacklist Redis
        if (accessToken != null && accessToken.startsWith("Bearer ")) {
            String jwt = accessToken.substring(7);
            long remainingExpirationSeconds = userJwtGenerator.getRemainingExpirationInSeconds(jwt);

            if (remainingExpirationSeconds > 0) {
                redisTemplate.opsForValue().set(
                        BLACKLIST_PREFIX + userJwtGenerator.extractTokenId(jwt),
                        "true",
                        Duration.ofSeconds(remainingExpirationSeconds)
                );
            }
        }
    }

    // ── Staff Management (CRUD) ─────────────────────────────────────

    private String hashRefreshToken(String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("Refresh token is required");
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    @Override
    @Transactional
    public UserResponse createUser(UserCreationRequest request, Long adminEnterpriseId) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new ConflictException("This email is already in use");
        }

        User newUser = userMapper.toEntity(request, adminEnterpriseId);
        newUser.setPassword(passwordEncoder.encode(newUser.getPassword()));

        User savedUser = userRepository.save(newUser);
        return userMapper.toResponse(savedUser);
    }

    @Override
    public List<UserResponse> getUsersByEnterprise(Long enterpriseId) {
        return userRepository.findAllByEnterpriseId(enterpriseId)
                .stream()
                .filter(this::isManagedStaff)
                .map(userMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    public List<UserResponse> getActiveCouriersByEnterprise(Long enterpriseId) {
        return userRepository.findAllByEnterpriseId(enterpriseId)
                .stream()
                .filter(user -> user.getRole() == Role.ROLE_LIVREUR && user.isActive())
                .map(userMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    public UserResponse getActiveCourier(Long userId, Long enterpriseId) {
        User courier = userRepository.findByIdAndEnterpriseId(userId, enterpriseId)
                .orElseThrow(() -> new ResourceNotFoundException("Courier not found with id " + userId));
        if (courier.getRole() != Role.ROLE_LIVREUR || !courier.isActive()) {
            throw new ResourceNotFoundException("Active courier not found with id " + userId);
        }
        return userMapper.toResponse(courier);
    }

    @Override
    public UserResponse getStaffMember(Long userId, Long enterpriseId) {
        User user = userRepository.findByIdAndEnterpriseId(userId, enterpriseId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "User not found with id " + userId));
        requireManagedStaff(user);
        return userMapper.toResponse(user);
    }

    @Override
    @Transactional
    public UserResponse toggleUserStatus(Long userId, Long enterpriseId, boolean active) {
        User user = userRepository.findByIdAndEnterpriseId(userId, enterpriseId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "User not found with id " + userId));
        requireManagedStaff(user);

        user.setActive(active);
        User savedUser = userRepository.save(user);
        return userMapper.toResponse(savedUser);
    }

    @Override
    @Transactional
    public void deleteUser(Long userId, Long enterpriseId) {
        User user = userRepository.findByIdAndEnterpriseId(userId, enterpriseId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "User not found with id " + userId));
        requireManagedStaff(user);

        // Keep the row so historical lead, order, delivery, and audit references
        // remain attributable. "Delete" is therefore a recoverable deactivation.
        user.setActive(false);
        userRepository.save(user);
    }

    // ── Profile Operations ──────────────────────────────────────────

    @Override
    public UserResponse getProfile(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "User not found with email " + email));
        return userMapper.toResponse(user);
    }

    @Override
    @Transactional
    public UserResponse updateProfile(String email, ProfileUpdateRequest request) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "User not found with email " + email));

        // Partial update: only update fields that are provided (non-null)
        if (request.getFirstname() != null) {
            user.setFirstname(request.getFirstname());
        }
        if (request.getLastname() != null) {
            user.setLastname(request.getLastname());
        }
        if (request.getPhone() != null) {
            user.setPhone(request.getPhone());
        }
        // updatedAt is set automatically via @PreUpdate

        User savedUser = userRepository.save(user);
        return userMapper.toResponse(savedUser);
    }

    @Override
    @Transactional
    public void changePassword(String email, ChangePasswordRequest request) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "User not found with email " + email));

        // Verify current password
        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            throw new IllegalArgumentException("Current password is incorrect");
        }

        // Verify new password matches confirmation
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new IllegalArgumentException("New password and confirmation do not match");
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
    }

    @Override
    public UserResponse getUserById(Long userId, Long enterpriseId) {
        User user = userRepository.findByIdAndEnterpriseId(userId, enterpriseId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID " + userId));
        return userMapper.toResponse(user);
    }

    private boolean isManagedStaff(User user) {
        return user.getRole() == Role.ROLE_CSM
                || user.getRole() == Role.ROLE_LOGISTIC
                || user.getRole() == Role.ROLE_LIVREUR;
    }

    private void requireManagedStaff(User user) {
        if (!isManagedStaff(user)) {
            throw new IllegalArgumentException("Only staff accounts can be managed through this operation");
        }
    }
}
