package com.lifelink.dto;

import com.lifelink.model.enums.UserType;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests validating password policy enforcement (Design Decision #3).
 *
 * Rule: Minimum 8 characters, containing at least one uppercase letter,
 * one lowercase letter, and one digit.
 */
class PasswordPolicyValidationTest {

    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    private UserRegistrationDto createValidDtoWithPassword(String password) {
        return UserRegistrationDto.builder()
                .username("validuser")
                .email("valid@lifelink.org")
                .password(password)
                .fullName("Valid User")
                .phone("9876543210")
                .city("Bengaluru")
                .state("Karnataka")
                .userType(UserType.SEEKER)
                .build();
    }

    // =========================================================================
    // Valid Passwords
    // =========================================================================

    @ParameterizedTest(name = "Valid password accepted: {0}")
    @ValueSource(strings = {
            "Secret123",
            "P@ssw0rd2026",
            "SuperStrong9",
            "A1bcdefghijk",
            "Complex#Pass1"
    })
    @DisplayName("Password Policy: Valid passwords (>=8 chars, upper + lower + digit) produce 0 violations")
    void testValidPasswordsPass(String validPassword) {
        UserRegistrationDto dto = createValidDtoWithPassword(validPassword);
        Set<ConstraintViolation<UserRegistrationDto>> violations = validator.validate(dto);

        assertThat(violations)
                .noneMatch(v -> v.getPropertyPath().toString().equals("password"));
    }

    // =========================================================================
    // Invalid Passwords
    // =========================================================================

    @ParameterizedTest(name = "Too short password rejected: {0}")
    @ValueSource(strings = {
            "Sec12",
            "Ab1",
            "Short1A",
            "P@s1"
    })
    @DisplayName("Password Policy: Passwords under 8 characters fail validation")
    void testPasswordsTooShortFail(String shortPassword) {
        UserRegistrationDto dto = createValidDtoWithPassword(shortPassword);
        Set<ConstraintViolation<UserRegistrationDto>> violations = validator.validate(dto);

        assertThat(violations)
                .anyMatch(v -> v.getPropertyPath().toString().equals("password"));
    }

    @ParameterizedTest(name = "Missing uppercase rejected: {0}")
    @ValueSource(strings = {
            "secretpass123",
            "lowercase999",
            "alllower_1"
    })
    @DisplayName("Password Policy: Passwords missing uppercase letter fail validation")
    void testPasswordsMissingUppercaseFail(String noUpperPassword) {
        UserRegistrationDto dto = createValidDtoWithPassword(noUpperPassword);
        Set<ConstraintViolation<UserRegistrationDto>> violations = validator.validate(dto);

        assertThat(violations)
                .anyMatch(v -> v.getPropertyPath().toString().equals("password"));
    }

    @ParameterizedTest(name = "Missing lowercase rejected: {0}")
    @ValueSource(strings = {
            "SECRETPASS123",
            "UPPERCASE999",
            "ALLUPPER_1"
    })
    @DisplayName("Password Policy: Passwords missing lowercase letter fail validation")
    void testPasswordsMissingLowercaseFail(String noLowerPassword) {
        UserRegistrationDto dto = createValidDtoWithPassword(noLowerPassword);
        Set<ConstraintViolation<UserRegistrationDto>> violations = validator.validate(dto);

        assertThat(violations)
                .anyMatch(v -> v.getPropertyPath().toString().equals("password"));
    }

    @ParameterizedTest(name = "Missing digit rejected: {0}")
    @ValueSource(strings = {
            "SecretPassword",
            "NoDigitsAtAll",
            "UpperAndLowerOnly"
    })
    @DisplayName("Password Policy: Passwords missing digit fail validation")
    void testPasswordsMissingDigitFail(String noDigitPassword) {
        UserRegistrationDto dto = createValidDtoWithPassword(noDigitPassword);
        Set<ConstraintViolation<UserRegistrationDto>> violations = validator.validate(dto);

        assertThat(violations)
                .anyMatch(v -> v.getPropertyPath().toString().equals("password"));
    }

    @Test
    @DisplayName("Password Policy: Blank or null passwords fail validation")
    void testBlankAndNullPasswordsFail() {
        UserRegistrationDto blankDto = createValidDtoWithPassword("");
        assertThat(validator.validate(blankDto))
                .anyMatch(v -> v.getPropertyPath().toString().equals("password"));

        UserRegistrationDto nullDto = createValidDtoWithPassword(null);
        assertThat(validator.validate(nullDto))
                .anyMatch(v -> v.getPropertyPath().toString().equals("password"));
    }
}
