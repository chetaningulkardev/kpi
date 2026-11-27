package com.example.kpi.repository;

import com.example.kpi.entity.KpiAssignment;
import com.example.kpi.entity.Role;
import com.example.kpi.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for KPI Assignment entity - manages template assignments to organizational units
 */
@Repository
public interface KpiAssignmentRepository extends JpaRepository<KpiAssignment, Long> {
    List<KpiAssignment> findByUserAndActive(User user, Boolean active);

    List<KpiAssignment> findByRoleAndActive(Role role, Boolean active);

    List<KpiAssignment> findByAssignmentTypeAndActive(KpiAssignment.AssignmentType assignmentType, Boolean active);

    List<KpiAssignment> findByActiveTrue();

    List<KpiAssignment> findByUserAndAssignmentType(User user, KpiAssignment.AssignmentType assignmentType);
}

