package com.messagepulse.core.security;

import com.fasterxml.jackson.core.type.TypeReference;
import com.messagepulse.core.entity.ApiKey;
import com.messagepulse.core.repository.ApiKeyRepository;
import com.messagepulse.core.util.JsonUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ApiKeyAuthenticationFilterTest {

    @Mock
    private ApiKeyRepository apiKeyRepository;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    private ApiKeyAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        filter = new ApiKeyAuthenticationFilter(apiKeyRepository);
        SecurityContextHolder.clearContext();
    }

    @Test
    void testDoFilterInternal_ValidApiKey_SetsAuthentication() throws ServletException, IOException {
        String apiKey = "test-api-key-123";
        String keyHash = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";
        List<String> scopes = Arrays.asList("message:send", "message:read");

        ApiKey apiKeyEntity = ApiKey.builder()
                .id("key-001")
                .tenantId("tenant-1")
                .keyHash(keyHash)
                .scopes(JsonUtils.toJson(scopes))
                .isActive(true)
                .expiresAt(LocalDateTime.now().plusDays(30))
                .build();

        when(request.getHeader("X-API-Key")).thenReturn(apiKey);
        when(apiKeyRepository.findByKeyHash(anyString())).thenReturn(Optional.of(apiKeyEntity));

        try (MockedStatic<JsonUtils> jsonUtilsMock = mockStatic(JsonUtils.class)) {
            jsonUtilsMock.when(() -> JsonUtils.fromJson(anyString(), any(TypeReference.class)))
                    .thenReturn(scopes);

            filter.doFilterInternal(request, response, filterChain);

            ApiKeyAuthentication auth = (ApiKeyAuthentication) SecurityContextHolder.getContext().getAuthentication();
            assertNotNull(auth);
            assertEquals("tenant-1", auth.getTenantId());
            assertEquals("key-001", auth.getApiKeyId());
            assertEquals(scopes, auth.getScopes());
            assertTrue(auth.isAuthenticated());

            verify(filterChain).doFilter(request, response);
        }
    }

    @Test
    void testDoFilterInternal_NoApiKey_ContinuesWithoutAuthentication() throws ServletException, IOException {
        when(request.getHeader("X-API-Key")).thenReturn(null);

        filter.doFilterInternal(request, response, filterChain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(filterChain).doFilter(request, response);
        verifyNoInteractions(apiKeyRepository);
    }

    @Test
    void testDoFilterInternal_EmptyApiKey_ContinuesWithoutAuthentication() throws ServletException, IOException {
        when(request.getHeader("X-API-Key")).thenReturn("");

        filter.doFilterInternal(request, response, filterChain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(filterChain).doFilter(request, response);
        verifyNoInteractions(apiKeyRepository);
    }

    @Test
    void testDoFilterInternal_InvalidApiKey_ContinuesWithoutAuthentication() throws ServletException, IOException {
        String apiKey = "invalid-key";

        when(request.getHeader("X-API-Key")).thenReturn(apiKey);
        when(apiKeyRepository.findByKeyHash(anyString())).thenReturn(Optional.empty());

        filter.doFilterInternal(request, response, filterChain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void testDoFilterInternal_InactiveApiKey_ContinuesWithoutAuthentication() throws ServletException, IOException {
        String apiKey = "test-api-key-123";

        ApiKey apiKeyEntity = ApiKey.builder()
                .id("key-001")
                .tenantId("tenant-1")
                .keyHash("hash")
                .isActive(false)
                .build();

        when(request.getHeader("X-API-Key")).thenReturn(apiKey);
        when(apiKeyRepository.findByKeyHash(anyString())).thenReturn(Optional.of(apiKeyEntity));

        filter.doFilterInternal(request, response, filterChain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void testDoFilterInternal_ExpiredApiKey_ContinuesWithoutAuthentication() throws ServletException, IOException {
        String apiKey = "test-api-key-123";

        ApiKey apiKeyEntity = ApiKey.builder()
                .id("key-001")
                .tenantId("tenant-1")
                .keyHash("hash")
                .isActive(true)
                .expiresAt(LocalDateTime.now().minusDays(1))
                .build();

        when(request.getHeader("X-API-Key")).thenReturn(apiKey);
        when(apiKeyRepository.findByKeyHash(anyString())).thenReturn(Optional.of(apiKeyEntity));

        filter.doFilterInternal(request, response, filterChain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void testDoFilterInternal_NullExpiresAt_SetsAuthentication() throws ServletException, IOException {
        String apiKey = "test-api-key-123";
        List<String> scopes = Arrays.asList("message:send");

        ApiKey apiKeyEntity = ApiKey.builder()
                .id("key-001")
                .tenantId("tenant-1")
                .keyHash("hash")
                .scopes(JsonUtils.toJson(scopes))
                .isActive(true)
                .expiresAt(null)
                .build();

        when(request.getHeader("X-API-Key")).thenReturn(apiKey);
        when(apiKeyRepository.findByKeyHash(anyString())).thenReturn(Optional.of(apiKeyEntity));

        try (MockedStatic<JsonUtils> jsonUtilsMock = mockStatic(JsonUtils.class)) {
            jsonUtilsMock.when(() -> JsonUtils.fromJson(anyString(), any(TypeReference.class)))
                    .thenReturn(scopes);

            filter.doFilterInternal(request, response, filterChain);

            ApiKeyAuthentication auth = (ApiKeyAuthentication) SecurityContextHolder.getContext().getAuthentication();
            assertNotNull(auth);
            assertEquals("tenant-1", auth.getTenantId());

            verify(filterChain).doFilter(request, response);
        }
    }

    @Test
    void testShouldNotFilter_ActuatorPath_ReturnsTrue() {
        when(request.getRequestURI()).thenReturn("/actuator/health");

        boolean result = filter.shouldNotFilter(request);

        assertTrue(result);
    }

    @Test
    void testShouldNotFilter_ApiPath_ReturnsFalse() {
        when(request.getRequestURI()).thenReturn("/api/v1/messages");

        boolean result = filter.shouldNotFilter(request);

        assertFalse(result);
    }
}
