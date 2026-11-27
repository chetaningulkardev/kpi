package com.example.kpi.repository;

import com.example.kpi.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RoleRepository extends JpaRepository<Role, Long> {
    Optional<Role> findByRoleName(String roleName);
    Optional<Role> findByRoleCode(String roleCode);
    boolean existsByRoleName(String roleName);
    boolean existsByRoleCode(String roleCode);
    java.util.List<Role> findByParentRoleId(Long parentRoleId);
}

