package com.example.kpi.repository;

import com.example.kpi.entity.UserPortfolioAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UserPortfolioAssignmentRepository extends JpaRepository<UserPortfolioAssignment, Long> {
    List<UserPortfolioAssignment> findByUserId(Long userId);
    List<UserPortfolioAssignment> findByPortfolioId(Long portfolioId);
    List<UserPortfolioAssignment> findByUserIdAndActiveTrue(Long userId);
    void deleteByUserId(Long userId);
    boolean existsByUserIdAndPortfolioId(Long userId, Long portfolioId);
}

