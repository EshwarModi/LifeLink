package com.lifelink.service;

import com.lifelink.dto.RegistrationForm;
import com.lifelink.model.User;
import com.lifelink.model.enums.UserType;
import com.lifelink.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

    @Test
    @DisplayName("Should hash raw password with BCrypt before saving user")
    void registerUser_HashesPasswordWithBCrypt() {
        RegistrationForm form = RegistrationForm.builder()
                .email("testdonor@lifelink.com")
                .password("rawSecretPassword123")
                .fullName("Test Donor")
                .phone("9876543210")
                .userType(UserType.DONOR)
                .bloodGroup("O+")
                .age(30)
                .gender("Female")
                .isAvailable(true)
                .build();

        when(userRepository.existsByEmail("testdonor@lifelink.com")).thenReturn(false);
        when(passwordEncoder.encode("rawSecretPassword123")).thenReturn("$2a$10$hashedBCryptValueHere");
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        User savedUser = userService.registerUser(form);

        assertNotNull(savedUser);
        assertEquals("$2a$10$hashedBCryptValueHere", savedUser.getPassword());
        assertEquals("testdonor@lifelink.com", savedUser.getEmail());
        assertNotNull(savedUser.getDonorProfile());
        assertEquals("O+", savedUser.getDonorProfile().getBloodGroup());

        verify(passwordEncoder, times(1)).encode("rawSecretPassword123");
        verify(userRepository, times(1)).save(any(User.class));
    }
}
