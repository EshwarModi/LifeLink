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
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

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
        rateLimitingFilter.setTrustForwardedHeaders(false);
        rateLimitingFilter.setLoginCapacity(RateLimitingFilter.DEFAULT_LOGIN_CAPACITY);
        rateLimitingFilter.setRegisterCapacity(RateLimitingFilter.DEFAULT_REGISTER_CAPACITY);
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
    @DisplayName("Public routes (/, /login, /register) are accessible without authentication")
    void testPublicRoutesAccessible() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(view().name("index"))
                .andExpect(model().attributeExists("totalDonors", "totalRequests", "fulfilledRequests"));

        mockMvc.perform(get("/login"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/register"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Anonymous GET /api/donors and /api/requests return HTTP 401 JSON instead of HTML login redirect")
    void testAnonymousApiCallsReturn401Json() throws Exception {
        mockMvc.perform(get("/api/donors"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error", is("Authentication required")));

        mockMvc.perform(get("/api/requests"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error", is("Authentication required")));
    }

    @Test
    @WithMockUser(username = "donor_user", roles = {"DONOR"})
    @DisplayName("ROLE_DONOR on GET /api/donors returns HTTP 403 JSON")
    void testDonorForbiddenOnApiDonorsReturns403Json() throws Exception {
        mockMvc.perform(get("/api/donors"))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error", is("Access denied")));
    }

    @Test
    @WithMockUser(username = "seeker_user", roles = {"SEEKER"})
    @DisplayName("ROLE_SEEKER on GET /api/donors returns HTTP 200")
    void testSeekerAllowedOnApiDonors() throws Exception {
        mockMvc.perform(get("/api/donors"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
    }

    @Test
    @WithMockUser(username = "admin_user", roles = {"ADMIN"})
    @DisplayName("ROLE_ADMIN on GET /api/donors returns HTTP 200")
    void testAdminAllowedOnApiDonors() throws Exception {
        mockMvc.perform(get("/api/donors"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
    }

    @Test
    @WithMockUser(username = "donor_user", roles = {"DONOR"})
    @DisplayName("ROLE_DONOR on GET /api/requests returns HTTP 200")
    void testDonorAllowedOnApiRequests() throws Exception {
        mockMvc.perform(get("/api/requests"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
    }

    @Test
    @DisplayName("Anonymous GET /actuator/health and probe sub-paths return 200 with status UP only (no components or details)")
    void testAnonymousActuatorHealthReturnsStatusOnly() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("UP")))
                .andExpect(jsonPath("$.components").doesNotExist())
                .andExpect(jsonPath("$.details").doesNotExist());

        mockMvc.perform(get("/actuator/health/liveness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("UP")));

        mockMvc.perform(get("/actuator/health/readiness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("UP")));
    }

    @Test
    @DisplayName("Anonymous GET /actuator/env and /actuator/beans are rejected with HTTP 401 JSON")
    void testAnonymousActuatorEnvAndBeansRejectedWith401() throws Exception {
        mockMvc.perform(get("/actuator/env"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error", is("Authentication required")));

        mockMvc.perform(get("/actuator/beans"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error", is("Authentication required")));
    }

    @Test
    @WithMockUser(username = "donor_user", roles = {"DONOR"})
    @DisplayName("Non-admin (ROLE_DONOR) on /actuator/env is 403 and on /actuator/health sees status only")
    void testNonAdminActuatorAccess() throws Exception {
        mockMvc.perform(get("/actuator/env"))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error", is("Access denied")));

        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("UP")))
                .andExpect(jsonPath("$.components").doesNotExist())
                .andExpect(jsonPath("$.details").doesNotExist());
    }

    @Test
    @WithMockUser(username = "admin_user", roles = {"ADMIN"})
    @DisplayName("ROLE_ADMIN on GET /actuator/health sees full components and details; unexposed /actuator/env returns 404")
    void testAdminActuatorHealthShowsDetailsAndUnexposedEndpointsReturn404() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("UP")))
                .andExpect(jsonPath("$.components").exists())
                .andExpect(jsonPath("$.components.db.status", is("UP")))
                .andExpect(jsonPath("$.components.diskSpace.status", is("UP")));

        mockMvc.perform(get("/actuator/env"))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/actuator/beans"))
                .andExpect(status().isNotFound());
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
    @DisplayName("Rate limiting: 11th POST /login in a minute returns HTTP 429 with Retry-After, while a different IP is unaffected")
    void testRateLimiterReturns429OnExcessiveLoginAttempts() throws Exception {
        // Send 10 requests consuming the 10-token capacity for 127.0.0.1
        for (int i = 0; i < 10; i++) {
            mockMvc.perform(post("/login")
                            .with(csrf())
                            .param("username", "bad_user")
                            .param("password", "bad_pass"))
                    .andReturn();
        }

        // 11th request from 127.0.0.1 must be rejected with HTTP 429 and Retry-After header
        mockMvc.perform(post("/login")
                        .with(csrf())
                        .param("username", "bad_user")
                        .param("password", "bad_pass"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER));

        // A different IP (203.0.113.55) must be unaffected and processed normally
        mockMvc.perform(post("/login")
                        .with(csrf())
                        .with(request -> {
                            request.setRemoteAddr("203.0.113.55");
                            return request;
                        })
                        .param("username", "test_donor")
                        .param("password", "Secret123"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/donor/dashboard"));
    }

    @Test
    @DisplayName("Rate limiting (trust=false): Changing X-Forwarded-For between requests does NOT reset the limit")
    void testRateLimiterIgnoresXForwardedForWhenTrustIsFalse() throws Exception {
        rateLimitingFilter.setTrustForwardedHeaders(false);

        // Send 10 requests with different spoofed X-Forwarded-For headers from the same remoteAddr
        for (int i = 1; i <= 10; i++) {
            mockMvc.perform(post("/login")
                            .with(csrf())
                            .header("X-Forwarded-For", "198.51.100." + i)
                            .param("username", "bad_user")
                            .param("password", "bad_pass"))
                    .andReturn();
        }

        // 11th request with yet another spoofed X-Forwarded-For header is still rejected with 429
        mockMvc.perform(post("/login")
                        .with(csrf())
                        .header("X-Forwarded-For", "198.51.100.250")
                        .param("username", "bad_user")
                        .param("password", "bad_pass"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER));
    }

    @Test
    @DisplayName("Rate limiting (trust=true): Keys buckets on the first IP of X-Forwarded-For")
    void testRateLimiterKeysOnXForwardedForWhenTrustIsTrue() throws Exception {
        rateLimitingFilter.setTrustForwardedHeaders(true);

        // Exhaust the 10-token bucket for forwarded IP 203.0.113.10
        for (int i = 0; i < 10; i++) {
            mockMvc.perform(post("/login")
                            .with(csrf())
                            .header("X-Forwarded-For", "203.0.113.10, 10.0.0.1")
                            .param("username", "bad_user")
                            .param("password", "bad_pass"))
                    .andReturn();
        }

        // 11th request for 203.0.113.10 is rejected with 429 and Retry-After
        mockMvc.perform(post("/login")
                        .with(csrf())
                        .header("X-Forwarded-For", "203.0.113.10, 10.0.0.1")
                        .param("username", "bad_user")
                        .param("password", "bad_pass"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER));

        // Request with a different X-Forwarded-For IP (203.0.113.20) is unaffected
        mockMvc.perform(post("/login")
                        .with(csrf())
                        .header("X-Forwarded-For", "203.0.113.20, 10.0.0.1")
                        .param("username", "test_donor")
                        .param("password", "Secret123"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/donor/dashboard"));
    }
}
