package com.example.kpi.repository;

import com.example.kpi.entity.Tribe;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TribeRepository extends JpaRepository<Tribe, Long> {
    Optional<Tribe> findByTribeName(String tribeName);
    Optional<Tribe> findByTribeCode(String tribeCode);
    boolean existsByTribeName(String tribeName);
    boolean existsByTribeCode(String tribeCode);
    List<Tribe> findByActiveTrue();

    /** Tribes the user leads. */
    @org.springframework.data.jpa.repository.Query(
            "select t.id from Tribe t join t.leads l where l.id = :userId")
    java.util.Set<Long> findTribeIdsLedBy(
            @org.springframework.data.repository.query.Param("userId") Long userId);
}

