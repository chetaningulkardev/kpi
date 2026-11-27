package com.example.kpi.repository;

import com.example.kpi.entity.LookupValue;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LookupValueRepository extends JpaRepository<LookupValue, Long> {
    List<LookupValue> findByLookupTypeAndDeletedFalseOrderByLabelAsc(String lookupType);

    Optional<LookupValue> findByLookupTypeAndCodeAndDeletedFalse(String lookupType, String code);
}

