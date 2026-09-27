package com.lifelink;

import com.lifelink.model.User;
import com.lifelink.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class RegistrationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Test
    @DisplayName("Should successfully register seeker user via HTTP POST /register")
    void registerSeekerUser_Success() throws Exception {
        mockMvc.perform(post("/register")
                .with(csrf())
                .param("fullName", "Integration Seeker")
                .param("email", "seeker.integration@lifelink.com")
                .param("password", "securePass123")
                .param("phone", "9876543210")
                .param("userType", "SEEKER"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?registered=true"));

        Optional<User> savedUser = userRepository.findByEmail("seeker.integration@lifelink.com");
        assertTrue(savedUser.isPresent());
        assertEquals("Integration Seeker", savedUser.get().getFullName());
    }
}
