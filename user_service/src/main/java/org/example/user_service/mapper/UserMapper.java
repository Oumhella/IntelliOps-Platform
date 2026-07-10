package org.example.user_service.mapper;

import org.example.user_service.dto.request.UserCreationRequest;
import org.example.user_service.dto.response.UserResponse;
import org.example.user_service.entity.AgentCSM;
import org.example.user_service.entity.AgentLogistic;
import org.example.user_service.entity.User;
import org.springframework.stereotype.Component;

@Component
public class UserMapper {
    public User toEntity(UserCreationRequest request, Long enterpriseId) {
        if (request == null) {
            return null;
        }

        User user;
        // Instanciation de la bonne classe fille selon l'héritage SINGLE_TABLE
        if ("CSM".equalsIgnoreCase(request.getRole())) {
            user = new AgentCSM();
        } else if ("LOGISTIC".equalsIgnoreCase(request.getRole())) {
            user = new AgentLogistic();
        } else {
            throw new IllegalArgumentException("Rôle inconnu : " + request.getRole());
        }

        user.setEmail(request.getEmail());
        user.setFirstname(request.getFirstname());
        user.setLastname(request.getLastname());
        user.setPhone(request.getPhone());
        user.setEnterpriseId(enterpriseId);
        user.setActive(true);

        // Note : Le mot de passe sera encodé/haché dans le Service
        // juste avant la sauvegarde pour des raisons de sécurité.
        user.setPassword(request.getPassword());

        return user;
    }

    /**
     * Convertit une entité JPA (User ou ses sous-classes) en DTO de réponse sécurisé.
     */
    public UserResponse toResponse(User user) {
        if (user == null) {
            return null;
        }

        return UserResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .firstname(user.getFirstname())
                .lastname(user.getLastname())
                .phone(user.getPhone())
                .role(user.getRole() != null ? user.getRole().name() : null)
                .isActive(user.isActive())
                .build();
    }
}