package org.example.user_service.service;

import lombok.RequiredArgsConstructor;
import org.example.user_service.config.UserJwtGenerator;
import org.example.user_service.dto.request.ChangePasswordRequest;
import org.example.user_service.dto.request.ProfileUpdateRequest;
import org.example.user_service.dto.request.RegisterRequest;
import org.example.user_service.dto.request.UserCreationRequest;
import org.example.user_service.dto.response.UserResponse;
import org.example.user_service.entity.Admin;
import org.example.user_service.entity.User;
import org.example.common.exception.ConflictException;
import org.example.common.exception.ResourceNotFoundException;
import org.example.user_service.mapper.UserMapper;
import org.example.user_service.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.example.user_service.dto.response.AuthResponse;
import org.example.user_service.dto.request.LoginRequest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserMapper userMapper;
    private final UserJwtGenerator userJwtGenerator;

    // ── Authentication ──────────────────────────────────────────────

    @Override
    @Transactional
    public UserResponse register(RegisterRequest request) {
        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new ConflictException("This email is already in use");
        }

        Long newEnterpriseId = System.currentTimeMillis();

        Admin admin = userMapper.toAdminEntity(request, newEnterpriseId);
        admin.setPassword(passwordEncoder.encode(admin.getPassword()));
        // createdAt is now set automatically via @PrePersist

        User savedAdmin = userRepository.save(admin);
        return userMapper.toResponse(savedAdmin);
    }

    @Override
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new IllegalArgumentException("Invalid credentials"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new IllegalArgumentException("Invalid credentials");
        }

        if (!user.isActive()) {
            throw new IllegalArgumentException("This account is deactivated");
        }

        String realToken = userJwtGenerator.generateToken(user);

        return new AuthResponse(
                realToken,
                user.getEmail(),
                user.getFirstname(),
                user.getLastname(),
                user.getRole().name(),
                user.getEnterpriseId()
        );
    }

    // ── Staff Management (CRUD) ─────────────────────────────────────

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
                .map(userMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    public UserResponse getStaffMember(Long userId, Long enterpriseId) {
        User user = userRepository.findByIdAndEnterpriseId(userId, enterpriseId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "User not found with id " + userId));
        return userMapper.toResponse(user);
    }

    @Override
    @Transactional
    public UserResponse toggleUserStatus(Long userId, Long enterpriseId, boolean active) {
        User user = userRepository.findByIdAndEnterpriseId(userId, enterpriseId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "User not found with id " + userId));

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

        userRepository.delete(user);
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
    public UserResponse getUserById(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID " + userId));
        return userMapper.toResponse(user);
    }
}
