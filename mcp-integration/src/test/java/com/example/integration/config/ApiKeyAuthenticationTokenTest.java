package com.example.integration.config;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ApiKeyAuthenticationTokenTest {

    @Test
    void isAuthenticatedAfterConstruction() {
        var token = new ApiKeyAuthenticationToken(
                "my-key", List.of(new SimpleGrantedAuthority("ROLE_API")));
        assertThat(token.isAuthenticated()).isTrue();
    }

    @Test
    void getPrincipalReturnsKey() {
        var token = new ApiKeyAuthenticationToken(
                "my-key", List.of(new SimpleGrantedAuthority("ROLE_API")));
        assertThat(token.getPrincipal()).isEqualTo("my-key");
    }

    @Test
    void getCredentialsReturnsNull() {
        var token = new ApiKeyAuthenticationToken(
                "my-key", List.of(new SimpleGrantedAuthority("ROLE_API")));
        assertThat(token.getCredentials()).isNull();
    }

    @Test
    void hasCorrectAuthorities() {
        var authority = new SimpleGrantedAuthority("ROLE_API");
        var token = new ApiKeyAuthenticationToken("my-key", List.of(authority));
        assertThat(token.getAuthorities())
                .hasSize(1)
                .contains(authority);
    }

    @Test
    void constructorWithEmptyAuthorities() {
        var token = new ApiKeyAuthenticationToken("key", List.of());
        assertThat(token.getAuthorities()).isEmpty();
        assertThat(token.isAuthenticated()).isTrue();
    }
}
