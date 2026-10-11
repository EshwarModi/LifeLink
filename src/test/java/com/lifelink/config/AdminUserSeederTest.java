package com.lifelink.config;

import com.lifelink.model.User;
import com.lifelink.model.enums.UserType;
import com.lifelink.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Focused tests for {@link AdminUserSeeder} startup validation and seeding behavior using {@link ApplicationContextRunner}.
 */
class AdminUserSeederTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(org.springframework.boot.autoconfigure.AutoConfigurations.of(
                    PropertyPlaceholderAutoConfiguration.class
            ))
            .withBean(UserRepository.class, () -> mock(UserRepository.class))
            .withBean(PasswordEncoder.class, BCryptPasswordEncoder::new)
            .withUserConfiguration(AdminUserSeeder.class);

    @Test
    @DisplayName("AdminUserSeeder: Context fails to start when lifelink.admin.password / ADMIN_PASSWORD is missing")
    void testContextFailsWhenAdminPasswordIsMissing() {
        contextRunner.run(context -> {
            assertThat(context).hasFailed();
        });
    }

    @ParameterizedTest(name = "Weak password [{index}] rejected at startup")
    @ValueSource(strings = {
            "",
            "   ",
            "Short1",
            "alllowercase123",
            "ALLUPPERCASE123",
            "NoDigitsInThisPassword"
    })
    @DisplayName("AdminUserSeeder: Context fails to start when ADMIN_PASSWORD violates password policy and never logs the password")
    void testContextFailsWhenAdminPasswordIsWeak(String weakPassword) {
        contextRunner
                .withPropertyValues("lifelink.admin.password=" + weakPassword)
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseInstanceOf(IllegalStateException.class)
                            .rootCause()
                            .hasMessageContaining("Invalid lifelink.admin.password");
                    if (!weakPassword.isBlank()) {
                        assertThat(context.getStartupFailure().getMessage()).doesNotContain(weakPassword);
                    }
                });
    }

    @Test
    @DisplayName("AdminUserSeeder: Context starts and seeds admin user when ADMIN_PASSWORD is valid")
    void testSeederCreatesAdminWhenPasswordIsValid() {
        contextRunner
                .withPropertyValues(
                        "lifelink.admin.username=sysadmin",
                        "lifelink.admin.email=sysadmin@lifelink.org",
                        "lifelink.admin.password=ValidAdminPass9"
                )
                .run(context -> {
                    assertThat(context).hasNotFailed();

                    UserRepository userRepository = context.getBean(UserRepository.class);
                    PasswordEncoder passwordEncoder = context.getBean(PasswordEncoder.class);
                    AdminUserSeeder seeder = context.getBean(AdminUserSeeder.class);

                    when(userRepository.existsByUsername(anyString())).thenReturn(false);
                    when(userRepository.existsByEmail(anyString())).thenReturn(false);

                    seeder.run();

                    ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
                    verify(userRepository).save(userCaptor.capture());

                    User savedAdmin = userCaptor.getValue();
                    assertThat(savedAdmin.getUsername()).isEqualTo("sysadmin");
                    assertThat(savedAdmin.getEmail()).isEqualTo("sysadmin@lifelink.org");
                    assertThat(savedAdmin.getUserType()).isEqualTo(UserType.ADMIN);
                    assertThat(savedAdmin.isAdmin()).isTrue();
                    assertThat(passwordEncoder.matches("ValidAdminPass9", savedAdmin.getPasswordHash())).isTrue();
                });
    }
}
