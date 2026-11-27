package com.example.kpi.repository;

import com.example.kpi.entity.CalibrationSession;
import com.example.kpi.entity.ReviewCycle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for Calibration Session entity - manages calibration review sessions
 */
@Repository
public interface CalibrationSessionRepository extends JpaRepository<CalibrationSession, Long> {
    List<CalibrationSession> findByReviewCycle(ReviewCycle reviewCycle);
}

