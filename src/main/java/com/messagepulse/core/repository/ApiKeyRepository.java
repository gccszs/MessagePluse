package com.messagepulse.core.repository;

import com.messagepulse.core.entity.ApiKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ApiKeyRepository extends JpaRepository<ApiKey, String> {

    Optional<ApiKey> findByKeyHash(String keyHash);

    List<ApiKey> findByTenantIdAndIsActive(String tenantId, Boolean isActive);
}
