package com.example.operion.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.example.operion.module.auth.entity.User;
import com.example.operion.module.auth.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Sets the first password of an account a migration created LOCKED (password
 * hash {@value #LOCKED}), e.g. the HQ super admin (V11). Passwords never go in
 * a migration or a file, so it comes from the environment:
 * {@code BOOTSTRAP_ADMIN_EMAIL} + {@code BOOTSTRAP_ADMIN_PASSWORD}.
 *
 * Only a locked account is touched, so a real password is never overwritten
 * and leaving the variables set is harmless; remove them after the first
 * login anyway. There is no password-change endpoint yet.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InitialPasswordBootstrap implements ApplicationRunner {

    static final String LOCKED = "!";

    static final int MIN_LENGTH = 12;

    private final UserRepository userRepository;

    private final PasswordEncoder passwordEncoder;

    @Value("${bootstrap.admin.email:}")
    private String email;

    @Value("${bootstrap.admin.password:}")
    private String password;

    @Override
    public void run(ApplicationArguments args) {

        if (email.isBlank() || password.isBlank()) {
            return;
        }

        apply(email, password);
    }

    /** True if the password was set. */
    @Transactional
    public boolean apply(String email, String password) {

        if (password.length() < MIN_LENGTH) {
            log.warn("Initial password for {} not set: use at least {} characters", email, MIN_LENGTH);
            return false;
        }

        User user = userRepository.findByEmail(email.trim()).orElse(null);

        if (user == null || !LOCKED.equals(user.getPasswordHash())) {
            // missing, or already has a real password: never overwrite
            return false;
        }

        user.setPasswordHash(passwordEncoder.encode(password));
        userRepository.save(user);

        log.info("Initial password set for {}; remove BOOTSTRAP_ADMIN_PASSWORD now", email);
        return true;
    }
}
