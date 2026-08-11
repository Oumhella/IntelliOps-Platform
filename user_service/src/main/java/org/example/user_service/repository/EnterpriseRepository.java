package org.example.user_service.repository;

import org.example.user_service.entity.Enterprise;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EnterpriseRepository extends JpaRepository<Enterprise, Long> {
    List<Enterprise> findAllByOrderByCreatedAtDesc();
}
