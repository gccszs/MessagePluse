package com.messagepulse.core.config;

import com.messagepulse.core.repository.ApiKeyRepository;
import com.messagepulse.core.security.ApiKeyAuthenticationFilter;
import com.messagepulse.core.security.ApiKeyAuthenticationProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final ApiKeyRepository apiKeyRepository;
    private final ApiKeyAuthenticationProvider apiKeyAuthenticationProvider;

    public SecurityConfig(ApiKeyRepository apiKeyRepository, ApiKeyAuthenticationProvider apiKeyAuthenticationProvider) {
        this.apiKeyRepository = apiKeyRepository;
        this.apiKeyAuthenticationProvider = apiKeyAuthenticationProvider;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/**").permitAll()
                        .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        .anyRequest().authenticated()
                )
                .authenticationProvider(apiKeyAuthenticationProvider)
                .addFilterBefore(new ApiKeyAuthenticationFilter(apiKeyRepository), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
