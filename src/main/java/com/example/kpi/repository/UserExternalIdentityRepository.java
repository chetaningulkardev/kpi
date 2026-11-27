package com.example.kpi.repository;

import com.example.kpi.entity.UserExternalIdentity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserExternalIdentityRepository extends JpaRepository<UserExternalIdentity, Long> {

    Optional<UserExternalIdentity> findByUserIdAndProviderCodeAndActiveTrue(Long userId, String providerCode);

    List<UserExternalIdentity> findByUserId(Long userId);
}

