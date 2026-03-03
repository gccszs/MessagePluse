package com.messagepulse.core.service;

import com.messagepulse.core.dto.request.CreateApiKeyRequest;
import com.messagepulse.core.dto.response.ApiKeyResponse;
import com.messagepulse.core.entity.ApiKey;
import com.messagepulse.core.exception.AuthenticationException;
import com.messagepulse.core.repository.ApiKeyRepository;
import com.messagepulse.core.util.ApiKeyGenerator;
import com.messagepulse.core.util.JsonUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class ApiKeyService {

    private final ApiKeyRepository apiKeyRepository;

    public ApiKeyService(ApiKeyRepository apiKeyRepository) {
        this.apiKeyRepository = apiKeyRepository;
    }

    @Transactional
    public ApiKeyResponse createApiKey(CreateApiKeyRequest request) {
        String rawApiKey = ApiKeyGenerator.generateApiKey();
        String keyHash = ApiKeyGenerator.hashApiKey(rawApiKey);

        ApiKey apiKey = ApiKey.builder()
                .id(UUID.randomUUID().toString())
                .tenantId(request.getTenantId())
                .keyHash(keyHash)
                .scopes(JsonUtils.toJson(request.getScopes()))
                .rateLimit(request.getRateLimit() != null ? request.getRateLimit() : 100)
                .isActive(true)
                .expiresAt(request.getExpiresAt())
                .build();

        apiKeyRepository.save(apiKey);

        return ApiKeyResponse.builder()
                .apiKey(rawApiKey)
                .id(apiKey.getId())
                .tenantId(apiKey.getTenantId())
                .scopes(request.getScopes())
                .rateLimit(apiKey.getRateLimit())
                .expiresAt(apiKey.getExpiresAt())
                .createdAt(apiKey.getCreatedAt())
                .build();
    }

    public boolean validateApiKey(String rawApiKey) {
        String keyHash = ApiKeyGenerator.hashApiKey(rawApiKey);
        return apiKeyRepository.findByKeyHash(keyHash)
                .map(apiKey -> apiKey.getIsActive() &&
                        (apiKey.getExpiresAt() == null || apiKey.getExpiresAt().isAfter(LocalDateTime.now())))
                .orElse(false);
    }

    @Transactional
    public void revokeApiKey(String apiKeyId) {
        ApiKey apiKey = apiKeyRepository.findById(apiKeyId)
                .orElseThrow(() -> new AuthenticationException("API key not found: " + apiKeyId));
        apiKey.setIsActive(false);
        apiKeyRepository.save(apiKey);
    }
}
