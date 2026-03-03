package com.messagepulse.core.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class ApiKeyAuthentication implements Authentication {

    private final String tenantId;
    private final String apiKeyId;
    private final List<String> scopes;
    private boolean authenticated;

    public ApiKeyAuthentication(String tenantId, String apiKeyId, List<String> scopes) {
        this.tenantId = tenantId;
        this.apiKeyId = apiKeyId;
        this.scopes = scopes != null ? scopes : Collections.emptyList();
        this.authenticated = true;
    }

    public String getTenantId() {
        return tenantId;
    }

    public String getApiKeyId() {
        return apiKeyId;
    }

    public List<String> getScopes() {
        return scopes;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return Collections.emptyList();
    }

    @Override
    public Object getCredentials() {
        return null;
    }

    @Override
    public Object getDetails() {
        return null;
    }

    @Override
    public Object getPrincipal() {
        return tenantId;
    }

    @Override
    public boolean isAuthenticated() {
        return authenticated;
    }

    @Override
    public void setAuthenticated(boolean isAuthenticated) throws IllegalArgumentException {
        this.authenticated = isAuthenticated;
    }

    @Override
    public String getName() {
        return tenantId;
    }
}
