package com.example.kpi.repository;

import com.example.kpi.entity.Squad;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface SquadRepository extends JpaRepository<Squad, Long> {
    Optional<Squad> findBySquadName(String squadName);
    Optional<Squad> findBySquadCode(String squadCode);
    boolean existsBySquadName(String squadName);
    boolean existsBySquadCode(String squadCode);
    List<Squad> findByActiveTrue();
    List<Squad> findByPortfolioId(Long portfolioId);
    List<Squad> findByPortfolioIdIn(List<Long> portfolioIds);

    /** Squads the user leads. Replaces loading every squad and filtering in memory. */
    @Query("select s.id from Squad s join s.leads l where l.id = :userId")
    Set<Long> findSquadIdsLedBy(@Param("userId") Long userId);

    /** Squad ids under any of the given portfolios. */
    @Query("select s.id from Squad s where s.portfolio.id in :portfolioIds")
    Set<Long> findSquadIdsByPortfolioIds(@Param("portfolioIds") Collection<Long> portfolioIds);

    /** All member ids across the given squads, for reviewer-scope resolution. */
    @Query("select a.user.id from UserSquadAssignment a where a.squad.id in :squadIds")
    Set<Long> findMemberIdsBySquadIds(@Param("squadIds") Collection<Long> squadIds);
}
