package com.lifelink.security;

import com.lifelink.model.User;
import com.lifelink.model.enums.UserType;
import com.lifelink.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class SecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private RateLimitingFilter rateLimitingFilter;

    @BeforeEach
    void setUp() {
        rateLimitingFilter.resetBuckets();
        if (!userRepository.existsByUsername("test_donor")) {
            userRepository.save(User.builder()
                    .username("test_donor")
                    .email("donor@test.com")
                    .passwordHash(passwordEncoder.encode("Secret123"))
                    .fullName("Test Donor")
                    .phone("1111111111")
                    .city("Mumbai")
                    .state("Maharashtra")
                    .userType(UserType.DONOR)
                    .isAdmin(false)
                    .build());
        }
    }

    @Test
    @DisplayName("Public routes (/login, /register) are accessible without authentication")
    void testPublicRoutesAccessible() throws Exception {
        // Without controllers yet, /login view is handled by Spring Security form login
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Protected routes redirect unauthenticated users to /login")
    void testProtectedRoutesRequireAuth() throws Exception {
        mockMvc.perform(get("/donor/dashboard"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));

        mockMvc.perform(get("/seeker/dashboard"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));

        mockMvc.perform(get("/admin/dashboard"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    @WithMockUser(username = "donor_user", roles = {"DONOR"})
    @DisplayName("ROLE_DONOR cannot access /seeker/** or /admin/** (HTTP 403)")
    void testRoleDonorForbiddenOnSeekerAndAdmin() throws Exception {
        mockMvc.perform(get("/seeker/dashboard"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/admin/dashboard"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "seeker_user", roles = {"SEEKER"})
    @DisplayName("ROLE_SEEKER cannot access /donor/** or /admin/** (HTTP 403)")
    void testRoleSeekerForbiddenOnDonorAndAdmin() throws Exception {
        mockMvc.perform(get("/donor/dashboard"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/admin/dashboard"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("CSRF protection: POST /login without CSRF token is rejected (HTTP 403)")
    void testCsrfProtectionEnforced() throws Exception {
        mockMvc.perform(post("/login")
                        .param("username", "test_donor")
                        .param("password", "Secret123"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Form login: POST /login with valid credentials and CSRF redirects via success handler")
    void testSuccessfulFormLogin() throws Exception {
        mockMvc.perform(post("/login")
                        .with(csrf())
                        .param("username", "test_donor")
                        .param("password", "Secret123"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/donor/dashboard"));
    }

    @Test
    @DisplayName("Rate limiting: Excessive POST /login requests return HTTP 429")
    void testRateLimiterReturns429OnExcessiveLoginAttempts() throws Exception {
        // Send 10 requests consuming the 10-token capacity
        for (int i = 0; i < 10; i++) {
            mockMvc.perform(post("/login")
                            .with(csrf())
                            .param("username", "bad_user")
                            .param("password", "bad_pass"))
                    .andReturn();
        }

        // 11th request must be rejected by RateLimitingFilter with HTTP 429
        mockMvc.perform(post("/login")
                        .with(csrf())
                        .param("username", "bad_user")
                        .param("password", "bad_pass"))
                .andExpect(status().isTooManyRequests());
    }
}
