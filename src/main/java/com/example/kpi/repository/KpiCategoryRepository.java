package com.example.kpi.repository;
import com.example.kpi.entity.KpiCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
@Repository
public interface KpiCategoryRepository extends JpaRepository<KpiCategory, Long> {
    Optional<KpiCategory> findByCategoryCode(String categoryCode);
    List<KpiCategory> findByActiveOrderByDisplayOrder(Boolean active);
}
