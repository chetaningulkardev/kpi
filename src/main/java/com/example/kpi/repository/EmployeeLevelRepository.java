package com.example.kpi.repository;

import com.example.kpi.entity.EmployeeLevel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for Employee Level entity - manages employee level classifications
 */
@Repository
public interface EmployeeLevelRepository extends JpaRepository<EmployeeLevel, Long> {
    Optional<EmployeeLevel> findByLevelCode(String levelCode);

    List<EmployeeLevel> findByActiveTrue();
}

