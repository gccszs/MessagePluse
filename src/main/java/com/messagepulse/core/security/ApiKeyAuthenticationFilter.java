package com.messagepulse.core.security;

import com.fasterxml.jackson.core.type.TypeReference;
import com.messagepulse.core.entity.ApiKey;
import com.messagepulse.core.repository.ApiKeyRepository;
import com.messagepulse.core.util.JsonUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.List;

public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    private final ApiKeyRepository apiKeyRepository;

    public ApiKeyAuthenticationFilter(ApiKeyRepository apiKeyRepository) {
        this.apiKeyRepository = apiKeyRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String apiKey = request.getHeader("X-API-Key");

        if (apiKey != null && !apiKey.isEmpty()) {
            try {
                String keyHash = sha256(apiKey);
                ApiKey apiKeyEntity = apiKeyRepository.findByKeyHash(keyHash).orElse(null);

                if (apiKeyEntity != null && apiKeyEntity.getIsActive()) {
                    if (apiKeyEntity.getExpiresAt() == null || apiKeyEntity.getExpiresAt().isAfter(LocalDateTime.now())) {
                        List<String> scopes = JsonUtils.fromJson(apiKeyEntity.getScopes(), new TypeReference<List<String>>() {});
                        ApiKeyAuthentication authentication = new ApiKeyAuthentication(
                                apiKeyEntity.getTenantId(),
                                apiKeyEntity.getId(),
                                scopes
                        );
                        SecurityContextHolder.getContext().setAuthentication(authentication);
                    }
                }
            } catch (Exception e) {
                // Invalid API key, continue without authentication
            }
        }

        filterChain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/actuator/");
    }

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not found", e);
        }
    }
}
