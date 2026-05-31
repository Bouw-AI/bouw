package com.example.integration.config;

import com.example.integration.service.JwtService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.util.matcher.IpAddressMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
public class SecurityConfig {

    /** Swagger docs and the login endpoint are always public. */
    @Bean
    @Order(1)
    public SecurityFilterChain publicFilterChain(HttpSecurity http) throws Exception {
        http.securityMatcher(
                        "/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**",
                        "/api/auth/**",
                        "/actuator/**")
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .csrf(csrf -> csrf.disable());
        return http.build();
    }

    private static final IpAddressMatcher LOCALHOST_V4 = new IpAddressMatcher("127.0.0.1");
    private static final IpAddressMatcher LOCALHOST_V6 = new IpAddressMatcher("::1");

    private static boolean isLocalhost(String remoteAddr) {
        return LOCALHOST_V4.matches(remoteAddr) || LOCALHOST_V6.matches(remoteAddr);
    }

    /**
     * All /api/** endpoints require a valid JWT Bearer token or X-API-Key (when configured).
     * When no api-key is configured, /api/agent/** is open to localhost only.
     */
    @Bean
    @Order(2)
    public SecurityFilterChain apiFilterChain(
            HttpSecurity http,
            JwtService jwtService,
            @Value("${agent.api-key:}") String apiKey) throws Exception {

        var jwtFilter = new JwtAuthenticationFilter(jwtService);
        boolean apiKeyConfigured = apiKey != null && !apiKey.isBlank();

        http.securityMatcher("/api/**")
                .authorizeHttpRequests(auth -> {
                    if (apiKeyConfigured) {
                        auth.anyRequest().authenticated();
                    } else {
                        // no api-key: agent endpoints open to localhost, denied externally
                        auth.requestMatchers(req -> req.getRequestURI().startsWith("/api/agent/")
                                        && isLocalhost(req.getRemoteAddr())).permitAll()
                                .requestMatchers("/api/agent/**").denyAll()
                                .anyRequest().authenticated();
                    }
                })
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()));

        if (apiKeyConfigured) {
            var apiKeyFilter = new ApiKeyAuthenticationFilter(apiKey);
            http.addFilterBefore(apiKeyFilter, JwtAuthenticationFilter.class);
        }

        return http.build();
    }

    /** Static files (index.html, app.js, fonts) are served without authentication. */
    @Bean
    @Order(3)
    public SecurityFilterChain staticFilterChain(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .csrf(csrf -> csrf.disable());
        return http.build();
    }

    private CorsConfigurationSource corsConfigurationSource() {
        var config = new CorsConfiguration();
        config.setAllowedOriginPatterns(List.of("*"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
