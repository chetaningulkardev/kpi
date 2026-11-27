package com.example.kpi.repository;

import com.example.kpi.entity.Portfolio;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PortfolioRepository extends JpaRepository<Portfolio, Long> {
    Optional<Portfolio> findByPortfolioName(String portfolioName);
    Optional<Portfolio> findByPortfolioCode(String portfolioCode);
    boolean existsByPortfolioName(String portfolioName);
    boolean existsByPortfolioCode(String portfolioCode);
    List<Portfolio> findByActiveTrue();
    List<Portfolio> findByTribeId(Long tribeId);

    /** Portfolios the user leads. */
    @org.springframework.data.jpa.repository.Query(
            "select p.id from Portfolio p join p.leads l where l.id = :userId")
    java.util.Set<Long> findPortfolioIdsLedBy(
            @org.springframework.data.repository.query.Param("userId") Long userId);

    /** Portfolio ids under any of the given tribes. */
    @org.springframework.data.jpa.repository.Query(
            "select p.id from Portfolio p where p.tribe.id in :tribeIds")
    java.util.Set<Long> findPortfolioIdsByTribeIds(
            @org.springframework.data.repository.query.Param("tribeIds") java.util.Collection<Long> tribeIds);
}

