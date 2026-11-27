package com.example.kpi.repository;

import com.example.kpi.entity.UserSquadAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UserSquadAssignmentRepository extends JpaRepository<UserSquadAssignment, Long> {
    List<UserSquadAssignment> findByUserId(Long userId);
    List<UserSquadAssignment> findBySquadId(Long squadId);
    List<UserSquadAssignment> findByUserIdAndActiveTrue(Long userId);
    void deleteByUserId(Long userId);
    boolean existsByUserIdAndSquadId(Long userId, Long squadId);
}

