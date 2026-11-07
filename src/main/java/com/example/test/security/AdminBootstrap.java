package com.example.test.security;

import com.example.test.common.Emails;
import com.example.test.model.Role;
import com.example.test.model.User;
import com.example.test.repo.UserRepo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;


/** Creates the configured admin user on startup if it does not exist yet. Admins have no wallet. */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminBootstrap implements ApplicationRunner {

    private static final String DEFAULT_PASSWORD = "Admin@12345";

    private final UserRepo userRepo;
    private final PasswordEncoder passwordEncoder;
    private final WalletSecurityProperties properties;

    @Override
    public void run(ApplicationArguments args) {
        WalletSecurityProperties.Admin admin = properties.admin();
        if (admin == null || admin.email() == null || admin.email().isBlank()) {
            log.info("No bootstrap admin configured");
            return;
        }
        if (admin.password() == null || admin.password().isBlank()) {
            throw new IllegalStateException("wallet.security.admin.password must be set when admin email is set");
        }
        String email = Emails.normalise(admin.email());
        if (userRepo.existsByEmail(email)) {
            return;
        }
        userRepo.save(new User("System", "Administrator", email, null,
                passwordEncoder.encode(admin.password()), null, Role.ADMIN));
        log.info("Bootstrap admin {} created", email);
        if (DEFAULT_PASSWORD.equals(admin.password())) {
            log.warn("Bootstrap admin is using the default password. "
                    + "Set ADMIN_PASSWORD before running anywhere shared.");
        }
    }
}
