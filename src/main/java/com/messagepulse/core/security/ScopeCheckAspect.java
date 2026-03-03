package com.messagepulse.core.security;

import com.messagepulse.core.exception.AuthorizationException;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

@Aspect
@Component
public class ScopeCheckAspect {

    @Around("@annotation(com.messagepulse.core.security.RequireScope)")
    public Object checkScope(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        RequireScope requireScope = method.getAnnotation(RequireScope.class);

        if (requireScope != null) {
            String requiredScope = requireScope.value();
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

            if (authentication == null || !authentication.isAuthenticated()) {
                throw new AuthorizationException("Authentication required");
            }

            if (authentication instanceof ApiKeyAuthentication) {
                ApiKeyAuthentication apiKeyAuth = (ApiKeyAuthentication) authentication;
                if (!apiKeyAuth.getScopes().contains(requiredScope)) {
                    throw new AuthorizationException("Insufficient permissions: " + requiredScope + " scope required");
                }
            } else {
                throw new AuthorizationException("Invalid authentication type");
            }
        }

        return joinPoint.proceed();
    }
}
