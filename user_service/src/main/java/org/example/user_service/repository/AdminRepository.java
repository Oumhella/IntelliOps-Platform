package org.example.user_service.repository;

import org.example.user_service.entity.Admin;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AdminRepository extends JpaRepository<Admin, Long> {
    List<Admin> findAllByEnterpriseIdNotOrderByCreatedAtDesc(Long excludedEnterpriseId);
}
