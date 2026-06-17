package com.example.integration.auth;

import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class DefaultUserBootstrapTest {

    @Test
    void seedsConfiguredAndScreenshotUsersWhenBootstrapPasswordPresent() throws Exception {
        UserAccountRepository repository = mock(UserAccountRepository.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        when(encoder.encode("main-pass")).thenReturn("main-hash");
        when(encoder.encode("shot-pass")).thenReturn("shot-hash");

        DefaultUserBootstrap bootstrap = new DefaultUserBootstrap(
                repository,
                encoder,
                "main-user",
                "main-pass",
                "shot-user",
                "shot-pass");

        bootstrap.run(new DefaultApplicationArguments(new String[0]));

        verify(repository).saveOrUpdate(new UserAccount("main-user", "main-hash", true, java.util.List.of("ROLE_USER")));
        verify(repository).saveOrUpdate(new UserAccount("shot-user", "shot-hash", true, java.util.List.of("ROLE_USER")));
    }

    @Test
    void preservesExistingPrimaryUserButStillSeedsScreenshotUserWhenPrimaryPasswordBlank() throws Exception {
        UserAccountRepository repository = mock(UserAccountRepository.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        when(repository.findByUsername("main-user"))
                .thenReturn(Optional.of(new UserAccount("main-user", "existing", true, java.util.List.of("ROLE_USER"))));
        when(encoder.encode("shot-pass")).thenReturn("shot-hash");

        DefaultUserBootstrap bootstrap = new DefaultUserBootstrap(
                repository,
                encoder,
                "main-user",
                "",
                "shot-user",
                "shot-pass");

        bootstrap.run(new DefaultApplicationArguments(new String[0]));

        verify(repository, never()).saveOrUpdate(new UserAccount("main-user", "existing", true, java.util.List.of("ROLE_USER")));
        verify(repository).saveOrUpdate(new UserAccount("shot-user", "shot-hash", true, java.util.List.of("ROLE_USER")));
    }

    @Test
    void requiresPrimaryPasswordWhenPrimaryUserMissing() {
        UserAccountRepository repository = mock(UserAccountRepository.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        when(repository.findByUsername("main-user")).thenReturn(Optional.empty());

        DefaultUserBootstrap bootstrap = new DefaultUserBootstrap(
                repository,
                encoder,
                "main-user",
                "",
                "shot-user",
                "shot-pass");

        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                () -> bootstrap.run(new DefaultApplicationArguments(new String[0])));

        verify(repository, never()).saveOrUpdate(any());
    }
}
