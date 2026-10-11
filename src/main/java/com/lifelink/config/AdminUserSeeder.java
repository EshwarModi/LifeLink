package com.lifelink.config;

import com.lifelink.model.User;
import com.lifelink.model.enums.UserType;
import com.lifelink.repository.UserRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.regex.Pattern;

/**
 * Seeds a default administrator account upon application startup if none exists.
 *
 * <p>Driven by environment variables:
 * <ul>
 *   <li>{@code ADMIN_USERNAME} / {@code lifelink.admin.username} (default: admin)</li>
 *   <li>{@code ADMIN_EMAIL} / {@code lifelink.admin.email} (default: admin@lifelink.org)</li>
 *   <li>{@code ADMIN_PASSWORD} / {@code lifelink.admin.password} (required; must satisfy password policy)</li>
 *   <li>{@code ADMIN_FULL_NAME} / {@code lifelink.admin.full-name} (default: System Administrator)</li>
 * </ul>
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminUserSeeder implements CommandLineRunner {

    /**
     * Same password policy as user registration (Design Decision #3):
     * minimum 8 characters, at least one uppercase letter, one lowercase letter, and one digit.
     */
    private static final Pattern PASSWORD_POLICY_PATTERN =
            Pattern.compile("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).{8,}$");

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${lifelink.admin.username:admin}")
    private String adminUsername;

    @Value("${lifelink.admin.email:admin@lifelink.org}")
    private String adminEmail;

    @Value("${lifelink.admin.password}")
    private String adminPassword;

    @Value("${lifelink.admin.full-name:System Administrator}")
    private String adminFullName;

    @Value("${lifelink.admin.phone:9999999999}")
    private String adminPhone;

    @Value("${lifelink.admin.city:Mumbai}")
    private String adminCity;

    @Value("${lifelink.admin.state:Maharashtra}")
    private String adminState;

    @PostConstruct
    public void validateAdminPassword() {
        if (adminPassword == null || adminPassword.isBlank()
                || !PASSWORD_POLICY_PATTERN.matcher(adminPassword.trim()).matches()) {
            throw new IllegalStateException(
                    "Invalid lifelink.admin.password (ADMIN_PASSWORD): password must be at least 8 characters " +
                    "long and contain at least one uppercase letter, one lowercase letter, and one digit."
            );
        }
    }

    @Override
    @Transactional
    public void run(String... args) {
        validateAdminPassword();

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
