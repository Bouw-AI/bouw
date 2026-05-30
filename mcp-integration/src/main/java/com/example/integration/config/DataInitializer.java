package com.example.integration.config;

import com.example.integration.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class DataInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final UserService userService;
    private final String adminUsername;
    private final String adminPassword;

    public DataInitializer(
            UserService userService,
            @Value("${admin.username:admin}") String adminUsername,
            @Value("${admin.password:}") String adminPassword) {
        this.userService = userService;
        this.adminUsername = adminUsername;
        this.adminPassword = adminPassword;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (adminPassword == null || adminPassword.isBlank()) {
            log.warn("ADMIN_PASSWORD is not set — skipping admin user creation. " +
                     "Set ADMIN_PASSWORD in hugin.env and restart to create the admin account.");
            return;
        }
        if (userService.existsByUsername(adminUsername)) {
            log.debug("Admin user '{}' already exists.", adminUsername);
            return;
        }
        userService.createUser(adminUsername, adminPassword);
        log.info("Admin user '{}' created successfully.", adminUsername);
    }
}
