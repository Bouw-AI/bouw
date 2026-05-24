package com.example.integration.config;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;

import java.util.Collection;

/**
 * An {@link AbstractAuthenticationToken} representing a successfully
 * authenticated API-key request.
 *
 * <p>Always {@link #isAuthenticated() authenticated} once constructed.
 */
public class ApiKeyAuthenticationToken extends AbstractAuthenticationToken {

    private final transient Object principal;

    public ApiKeyAuthenticationToken(Object principal, Collection<? extends GrantedAuthority> authorities) {
        super(authorities);
        this.principal = principal;
        setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return null;
    }

    @Override
    public Object getPrincipal() {
        return principal;
    }
}