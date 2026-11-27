package com.example.kpi.repository;

import com.example.kpi.entity.UserTribeAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UserTribeAssignmentRepository extends JpaRepository<UserTribeAssignment, Long> {
    List<UserTribeAssignment> findByUserId(Long userId);
    List<UserTribeAssignment> findByTribeId(Long tribeId);
    List<UserTribeAssignment> findByUserIdAndActiveTrue(Long userId);
    void deleteByUserId(Long userId);
    boolean existsByUserIdAndTribeId(Long userId, Long tribeId);
}

