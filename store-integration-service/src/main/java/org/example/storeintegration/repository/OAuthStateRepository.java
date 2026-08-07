package org.example.storeintegration.repository;
import org.example.storeintegration.entity.OAuthState;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface OAuthStateRepository extends JpaRepository<OAuthState, Long> { Optional<OAuthState> findByStateHash(String stateHash); }
