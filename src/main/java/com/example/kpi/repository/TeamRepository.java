package com.example.kpi.repository;

import com.example.kpi.entity.Team;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TeamRepository extends JpaRepository<Team, Long> {
    Optional<Team> findByTeamName(String teamName);
    Optional<Team> findByTeamCode(String teamCode);
    boolean existsByTeamName(String teamName);
    boolean existsByTeamCode(String teamCode);
    List<Team> findByActiveTrue();
}

