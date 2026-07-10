package org.example.user_service.service;

import lombok.RequiredArgsConstructor;
import org.example.user_service.config.JwtUtils;
import org.example.user_service.dto.request.RegisterRequest;
import org.example.user_service.dto.request.UserCreationRequest;
import org.example.user_service.dto.response.UserResponse;
import org.example.user_service.entity.Admin;
import org.example.user_service.entity.User;
import org.example.user_service.exception.ConflictException;
import org.example.user_service.mapper.UserMapper;
import org.example.user_service.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.example.user_service.dto.response.AuthResponse;
import org.example.user_service.dto.request.LoginRequest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserMapper userMapper;
    private final JwtUtils jwtUtils; // <--- On injecte enfin notre utilitaire JWT !

    @Override
    @Transactional
    public UserResponse register(RegisterRequest request) {
        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new IllegalArgumentException("Cet email est déjà utilisé");
        }

        Long newEnterpriseId = System.currentTimeMillis();

        // Utilisation du mapper dédié à l'Admin
        Admin admin = userMapper.toAdminEntity(request, newEnterpriseId);
        admin.setPassword(passwordEncoder.encode(admin.getPassword()));
        admin.setCreatedAt(LocalDateTime.now());

        User savedAdmin = userRepository.save(admin);
        return userMapper.toResponse(savedAdmin);
    }

    @Override
    public AuthResponse login(LoginRequest request) {
        // 1. Recherche de l'utilisateur par email
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new IllegalArgumentException("Identifiants incorrects"));

        // 2. Vérification du mot de passe haché (BCrypt)
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new IllegalArgumentException("Identifiants incorrects");
        }

        // 3. Vérification du statut du compte
        if (!user.isActive()) {
            throw new IllegalArgumentException("Ce compte est désactivé");
        }

        // 4. PLUS DE MOCK ! Génération du vrai token sécurisé avec rôles et enterpriseId embarqués
        String realToken = jwtUtils.generateToken(user);

        // 5. Retour de la réponse complète pour Angular
        return new AuthResponse(
                realToken,
                user.getEmail(),
                user.getFirstname(),
                user.getLastname(),
                user.getRole().name(),
                user.getEnterpriseId()
        );
    }

    @Override
    public UserResponse createUser(UserCreationRequest request, Long adminEnterpriseId) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new ConflictException("Cet e-mail est déjà utilisé");
        }

        // 1. Utilisation du Mapper pour transformer la requête en Entité
        User newUser = userMapper.toEntity(request, adminEnterpriseId);

        // 2. Encodage sécurisé (SecOps) du mot de passe
        newUser.setPassword(passwordEncoder.encode(newUser.getPassword()));

        // 3. Persistance
        User savedUser = userRepository.save(newUser);

        // 4. Utilisation du Mapper pour retourner la Réponse
        return userMapper.toResponse(savedUser);
    }

    @Override
    public List<UserResponse> getUsersByEnterprise(Long enterpriseId) {
        return userRepository.findAllByEnterpriseId(enterpriseId)
                .stream()
                .map(userMapper::toResponse) // <--- Mapping ultra propre avec référence de méthode
                .collect(Collectors.toList());
    }
}
