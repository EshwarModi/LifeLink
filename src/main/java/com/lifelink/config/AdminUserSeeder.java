package com.lifelink.config;

import com.lifelink.model.User;
import com.lifelink.model.enums.UserType;
import com.lifelink.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Seeds a default administrator account upon application startup if none exists.
 *
 * <p>Driven by environment variables with sensible defaults:
 * <ul>
 *   <li>{@code ADMIN_USERNAME} / {@code lifelink.admin.username} (default: admin)</li>
 *   <li>{@code ADMIN_EMAIL} / {@code lifelink.admin.email} (default: admin@lifelink.org)</li>
 *   <li>{@code ADMIN_PASSWORD} / {@code lifelink.admin.password} (default: Admin@1234)</li>
 *   <li>{@code ADMIN_FULL_NAME} / {@code lifelink.admin.full-name} (default: System Administrator)</li>
 * </ul>
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminUserSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${lifelink.admin.username:${ADMIN_USERNAME:admin}}")
    private String adminUsername;

    @Value("${lifelink.admin.email:${ADMIN_EMAIL:admin@lifelink.org}}")
    private String adminEmail;

    @Value("${lifelink.admin.password:${ADMIN_PASSWORD:Admin@1234}}")
    private String adminPassword;

    @Value("${lifelink.admin.full-name:${ADMIN_FULL_NAME:System Administrator}}")
    private String adminFullName;

    @Value("${lifelink.admin.phone:${ADMIN_PHONE:9999999999}}")
    private String adminPhone;

    @Value("${lifelink.admin.city:${ADMIN_CITY:Mumbai}}")
    private String adminCity;

    @Value("${lifelink.admin.state:${ADMIN_STATE:Maharashtra}}")
    private String adminState;

    @Override
    @Transactional
    public void run(String... args) {
        String cleanUsername = adminUsername.trim();
        String cleanEmail = adminEmail.trim().toLowerCase();

        if (userRepository.existsByUsername(cleanUsername) || userRepository.existsByEmail(cleanEmail)) {
            log.info("Admin account ({}) already exists. Skipping admin seed.", cleanUsername);
            return;
        }

        User admin = User.builder()
                .username(cleanUsername)
                .email(cleanEmail)
                .passwordHash(passwordEncoder.encode(adminPassword.trim()))
                .fullName(adminFullName.trim())
                .phone(adminPhone.trim())
                .city(adminCity.trim())
                .state(adminState.trim())
                .userType(UserType.ADMIN)
                .isAdmin(true)
                .build();

        userRepository.save(admin);
        log.info("Successfully seeded administrator user account: username='{}', email='{}'", cleanUsername, cleanEmail);
    }
}
