package org.example.user_service.repository;

import org.example.user_service.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    
    Optional<User> findByEmail(String email);

    List<User> findAllByEnterpriseId(Long enterpriseId);

    // Multi-tenant + Sécurité : Trouver un utilisateur spécifique au sein de son entreprise
    Optional<User> findByIdAndEnterpriseId(Long id, Long enterpriseId);

    //Utile pour l'Admin : Vérifier si un email existe déjà dans le système avant création
    boolean existsByEmail(String email);

    @Query("select count(distinct u.enterpriseId) from User u where u.enterpriseId <> 0")
    long countBusinessEnterprises();

    @Query("select count(u) from User u where u.enterpriseId <> 0")
    long countBusinessUsers();

    @Query("select count(u) from User u where u.enterpriseId <> 0 and u.isActive = true")
    long countActiveBusinessUsers();

    long countByEnterpriseId(Long enterpriseId);
}
