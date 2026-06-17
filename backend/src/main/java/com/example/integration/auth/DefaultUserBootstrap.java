package com.example.integration.auth;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DefaultUserBootstrap implements ApplicationRunner {

    private final UserAccountRepository userAccountRepository;
    private final PasswordEncoder passwordEncoder;
    private final String username;
    private final String password;
    private final String screenshotUsername;
    private final String screenshotPassword;

    public DefaultUserBootstrap(
            UserAccountRepository userAccountRepository,
            PasswordEncoder passwordEncoder,
            @Value("${auth.bootstrap.username:test}") String username,
            @Value("${auth.bootstrap.password:}") String password,
            @Value("${auth.test-user.username:screenshot-test}") String screenshotUsername,
            @Value("${auth.test-user.password:hugin-screenshot}") String screenshotPassword) {
        this.userAccountRepository = userAccountRepository;
        this.passwordEncoder = passwordEncoder;
        this.username = username;
        this.password = password;
        this.screenshotUsername = screenshotUsername;
        this.screenshotPassword = screenshotPassword;
    }

    @Override
    public void run(ApplicationArguments args) {
        if ((password == null || password.isBlank()) && userAccountRepository.findByUsername(username).isPresent()) {
            ensureUser(screenshotUsername, screenshotPassword);
            return;
        }
        ensureUser(username, resolvePassword());
        ensureUser(screenshotUsername, screenshotPassword);
    }

    private String resolvePassword() {
        if (password == null || password.isBlank()) {
            throw new IllegalStateException(
                    "auth.bootstrap.password must be set; refusing to start with a default password");
        }
        return password;
    }

    private void ensureUser(String username, String password) {
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            throw new IllegalStateException("Bootstrap usernames and passwords must not be blank");
        }
        userAccountRepository.saveOrUpdate(
                new UserAccount(username, passwordEncoder.encode(password), true, List.of("ROLE_USER")));
    }
}
