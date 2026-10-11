package com.lifelink.controller;

import com.lifelink.model.DonorProfile;
import com.lifelink.model.Match;
import com.lifelink.model.SeekerRequest;
import com.lifelink.model.User;
import com.lifelink.model.enums.BloodGroup;
import com.lifelink.model.enums.Gender;
import com.lifelink.model.enums.MatchStatus;
import com.lifelink.model.enums.RequestStatus;
import com.lifelink.model.enums.Urgency;
import com.lifelink.model.enums.UserType;
import com.lifelink.repository.DonorProfileRepository;
import com.lifelink.repository.MatchRepository;
import com.lifelink.repository.SeekerRequestRepository;
import com.lifelink.repository.UserRepository;
import com.lifelink.security.CustomUserDetails;
import com.lifelink.security.RateLimitingFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end MockMvc integration tests using the H2 in-memory test profile.
 *
 * Covers:
 * 1. User registration (POST /register)
 * 2. User login (POST /login) with success and invalid credentials
 * 3. Seeker request creation (POST /requests)
 * 4. Donor match acceptance (POST /matches/{requestId}/accept)
 * 5. CSRF protection verification: Every POST request without a CSRF token MUST be rejected with HTTP 403 Forbidden.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class MockMvcFlowAndCsrfIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DonorProfileRepository donorProfileRepository;

    @Autowired
    private SeekerRequestRepository seekerRequestRepository;

    @Autowired
    private MatchRepository matchRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private RateLimitingFilter rateLimitingFilter;

    @MockBean
    private JavaMailSender javaMailSender;

    private User existingSeeker;
    private User existingDonor;
    private CustomUserDetails seekerDetails;
    private CustomUserDetails donorDetails;

    @BeforeEach
    void setUp() {
        rateLimitingFilter.resetBuckets();

        existingSeeker = userRepository.save(User.builder()
                .username("flow_seeker")
                .email("flow_seeker@lifelink.org")
                .passwordHash(passwordEncoder.encode("Password123"))
                .fullName("Flow Seeker")
                .phone("9876500001")
                .city("Bengaluru")
                .state("Karnataka")
                .userType(UserType.SEEKER)
                .build());
        seekerDetails = new CustomUserDetails(existingSeeker);

        existingDonor = userRepository.save(User.builder()
                .username("flow_donor")
                .email("flow_donor@lifelink.org")
                .passwordHash(passwordEncoder.encode("Password123"))
                .fullName("Flow Donor")
                .phone("9876500002")
                .city("Bengaluru")
                .state("Karnataka")
                .userType(UserType.DONOR)
                .build());

        DonorProfile profile = donorProfileRepository.save(DonorProfile.builder()
                .user(existingDonor)
                .bloodGroup(BloodGroup.O_POSITIVE)
                .age(28)
                .gender(Gender.MALE)
                .availableToDonate(true)
                .lastDonationDate(LocalDate.now().minusDays(100))
                .build());
        existingDonor.setDonorProfile(profile);
        donorDetails = new CustomUserDetails(existingDonor);
    }

    // =========================================================================
    // 1. User Registration Flow (POST /register)
    // =========================================================================

    @Test
    @DisplayName("MockMvc Registration: POST /register creates new donor with BCrypt password and redirects to /login")
    void testRegisterFlow_Success() throws Exception {
        mockMvc.perform(post("/register")
                        .with(csrf())
                        .param("username", "new_donor_2026")
                        .param("email", "new_donor_2026@lifelink.org")
                        .param("password", "Pass1234")
                        .param("fullName", "New Donor 2026")
                        .param("phone", "+919876543299")
                        .param("city", "Bengaluru")
                        .param("state", "Karnataka")
                        .param("userType", "DONOR")
                        .param("bloodGroup", "B_POSITIVE")
                        .param("age", "30")
                        .param("gender", "MALE"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?registered=true"));

        // Verify entity persisted in database with hashed password
        Optional<User> savedUser = userRepository.findByUsername("new_donor_2026");
        assertThat(savedUser).isPresent();
        assertThat(savedUser.get().getUserType()).isEqualTo(UserType.DONOR);
        assertThat(passwordEncoder.matches("Pass1234", savedUser.get().getPasswordHash())).isTrue();

        // Verify 1:1 DonorProfile was created automatically
        Optional<DonorProfile> savedProfile = donorProfileRepository.findByUserId(savedUser.get().getId());
        assertThat(savedProfile).isPresent();
        assertThat(savedProfile.get().getBloodGroup()).isEqualTo(BloodGroup.B_POSITIVE);
    }

    // =========================================================================
    // 2. User Authentication / Login Flow (POST /login)
    // =========================================================================

    @Test
    @DisplayName("MockMvc Login: POST /login with valid credentials redirects to user dashboard")
    void testLoginFlow_Success() throws Exception {
        mockMvc.perform(post("/login")
                        .with(csrf())
                        .param("username", "flow_donor")
                        .param("password", "Password123"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/donor/dashboard"));
    }

    @Test
    @DisplayName("MockMvc Login: POST /login with wrong password redirects to /login?error")
    void testLoginFlow_BadCredentials() throws Exception {
        mockMvc.perform(post("/login")
                        .with(csrf())
                        .param("username", "flow_donor")
                        .param("password", "WrongPassword!"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?error=true"));
    }

    // =========================================================================
    // 3. Create Seeker Request Flow (POST /requests)
    // =========================================================================

    @Test
    @DisplayName("MockMvc Create Request: Authenticated seeker posts request, persists OPEN status in DB")
    void testCreateRequestFlow_Success() throws Exception {
        mockMvc.perform(post("/requests")
                        .with(csrf())
                        .with(user(seekerDetails))
                        .param("bloodGroup", "O_POSITIVE")
                        .param("unitsNeeded", "3")
                        .param("urgency", "CRITICAL")
                        .param("reason", "Accident Emergency")
                        .param("hospitalName", "Manipal Hospital")
                        .param("hospitalAddress", "HAL Airport Road, Bengaluru")
                        .param("requiredBy", LocalDate.now().plusDays(2).toString()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/seeker/dashboard"));

        // Verify request exists in DB with OPEN status
        var requests = seekerRequestRepository.findBySeekerIdOrderByCreatedAtDesc(existingSeeker.getId());
        assertThat(requests).isNotEmpty();
        SeekerRequest created = requests.get(0);
        assertThat(created.getBloodGroup()).isEqualTo(BloodGroup.O_POSITIVE);
        assertThat(created.getStatus()).isEqualTo(RequestStatus.OPEN);
        assertThat(created.getUnitsNeeded()).isEqualTo(3);
    }

    // =========================================================================
    // 4. Accept Match Flow (POST /matches/{requestId}/accept)
    // =========================================================================

    @Test
    @DisplayName("MockMvc Accept Match: Authenticated eligible donor accepts match, reveals contact info")
    void testAcceptMatchFlow_Success() throws Exception {
        SeekerRequest request = seekerRequestRepository.save(SeekerRequest.builder()
                .seeker(existingSeeker)
                .bloodGroup(BloodGroup.O_POSITIVE)
                .unitsNeeded(1)
                .urgency(Urgency.HIGH)
                .reason("Scheduled surgery")
                .hospitalName("Fortis Hospital")
                .hospitalAddress("Bannerghatta Road, Bengaluru")
                .requiredBy(LocalDate.now().plusDays(4))
                .status(RequestStatus.OPEN)
                .build());

        mockMvc.perform(post("/matches/" + request.getId() + "/accept")
                        .with(csrf())
                        .with(user(donorDetails)))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/requests/" + request.getId()));

        // Verify match record in DB
        Optional<Match> acceptedMatch = matchRepository.findByRequestIdAndDonorId(request.getId(), existingDonor.getId());
        assertThat(acceptedMatch).isPresent();
        assertThat(acceptedMatch.get().getStatus()).isEqualTo(MatchStatus.ACCEPTED);
        assertThat(acceptedMatch.get().isContactShared()).isTrue();
    }

    @Test
    @DisplayName("MockMvc Accept Match: Attempting to accept an EXPIRED match returns HTTP 409 Conflict and contactShared stays false")
    void testAcceptExpiredMatch_Returns409AndContactSharedStaysFalse() throws Exception {
        SeekerRequest request = seekerRequestRepository.save(SeekerRequest.builder()
                .seeker(existingSeeker)
                .bloodGroup(BloodGroup.O_POSITIVE)
                .unitsNeeded(1)
                .urgency(Urgency.HIGH)
                .reason("Scheduled surgery")
                .hospitalName("Fortis Hospital")
                .hospitalAddress("Bannerghatta Road, Bengaluru")
                .requiredBy(LocalDate.now().plusDays(4))
                .status(RequestStatus.OPEN)
                .build());

        Match expiredMatch = matchRepository.save(Match.builder()
                .request(request)
                .donor(existingDonor)
                .status(MatchStatus.EXPIRED)
                .contactShared(false)
                .build());

        mockMvc.perform(post("/matches/" + request.getId() + "/accept")
                        .with(csrf())
                        .with(user(donorDetails)))
                .andExpect(status().isConflict());

        Match reloaded = matchRepository.findById(expiredMatch.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(MatchStatus.EXPIRED);
        assertThat(reloaded.isContactShared()).isFalse();
    }

    // =========================================================================
    // 5. CSRF Protection Rejection (Design Decision #1: CSRF ON everywhere)
    // =========================================================================

    @Test
    @DisplayName("CSRF Rejection: POST /register without CSRF token is rejected with HTTP 403 Forbidden")
    void testCsrfProtection_RegisterWithoutToken_Forbidden() throws Exception {
        mockMvc.perform(post("/register")
                        .param("username", "no_csrf_user")
                        .param("email", "nocsrf@lifelink.org")
                        .param("password", "Pass1234")
                        .param("fullName", "No Csrf")
                        .param("phone", "9876543210")
                        .param("city", "Bengaluru")
                        .param("state", "Karnataka")
                        .param("userType", "SEEKER"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("CSRF Rejection: POST /login without CSRF token is rejected with HTTP 403 Forbidden")
    void testCsrfProtection_LoginWithoutToken_Forbidden() throws Exception {
        mockMvc.perform(post("/login")
                        .param("username", "flow_donor")
                        .param("password", "Password123"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("CSRF Rejection: POST /requests without CSRF token is rejected with HTTP 403 Forbidden")
    void testCsrfProtection_CreateRequestWithoutToken_Forbidden() throws Exception {
        mockMvc.perform(post("/requests")
                        .with(user(seekerDetails))
                        .param("bloodGroup", "O_POSITIVE")
                        .param("unitsNeeded", "1")
                        .param("urgency", "LOW")
                        .param("hospitalName", "Apollo Hospital")
                        .param("hospitalAddress", "Bannerghatta Road")
                        .param("requiredBy", LocalDate.now().plusDays(5).toString()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("CSRF Rejection: POST /matches/{requestId}/accept without CSRF token is rejected with HTTP 403 Forbidden")
    void testCsrfProtection_AcceptMatchWithoutToken_Forbidden() throws Exception {
        SeekerRequest request = seekerRequestRepository.save(SeekerRequest.builder()
                .seeker(existingSeeker)
                .bloodGroup(BloodGroup.O_POSITIVE)
                .unitsNeeded(1)
                .urgency(Urgency.HIGH)
                .hospitalName("Fortis Hospital")
                .hospitalAddress("Bannerghatta Road")
                .requiredBy(LocalDate.now().plusDays(4))
                .status(RequestStatus.OPEN)
                .build());

        mockMvc.perform(post("/matches/" + request.getId() + "/accept")
                        .with(user(donorDetails)))
                .andExpect(status().isForbidden());
    }

    // =========================================================================
    // 6. Actuator Health Probe (/actuator/health)
    // =========================================================================

    @Test
    @DisplayName("Actuator Health: GET /actuator/health is publicly accessible and reports UP without exposing components")
    void testActuatorHealthEndpoint() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components").doesNotExist())
                .andExpect(jsonPath("$.details").doesNotExist());
    }

    // =========================================================================
    // 7. End-to-End Full Lifecycle Regression Flow
    // =========================================================================

    @Test
    @DisplayName("Full E2E Flow: register seeker -> register donor -> seeker posts request -> donor sees it -> donor accepts -> both see contact info")
    void testFullEndToEndLifecycleFlow() throws Exception {
        // Step 1: Register Seeker
        mockMvc.perform(post("/register")
                        .with(csrf())
                        .param("username", "e2e_seeker")
                        .param("email", "e2e_seeker@lifelink.org")
                        .param("password", "SeekerPass1")
                        .param("fullName", "Aarav Seeker")
                        .param("phone", "+919811112222")
                        .param("city", "Chennai")
                        .param("state", "Tamil Nadu")
                        .param("userType", "SEEKER"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?registered=true"));

        User registeredSeeker = userRepository.findByUsername("e2e_seeker").orElseThrow();
        CustomUserDetails e2eSeekerDetails = new CustomUserDetails(registeredSeeker);

        // Step 2: Register Donor (same city & blood group AB_POSITIVE)
        mockMvc.perform(post("/register")
                        .with(csrf())
                        .param("username", "e2e_donor")
                        .param("email", "e2e_donor@lifelink.org")
                        .param("password", "DonorPass1")
                        .param("fullName", "Diya Donor")
                        .param("phone", "+919833334444")
                        .param("city", "Chennai")
                        .param("state", "Tamil Nadu")
                        .param("userType", "DONOR")
                        .param("bloodGroup", "AB_POSITIVE")
                        .param("age", "26")
                        .param("gender", "FEMALE"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?registered=true"));

        User registeredDonor = userRepository.findByUsername("e2e_donor").orElseThrow();
        CustomUserDetails e2eDonorDetails = new CustomUserDetails(registeredDonor);

        // Step 3: Seeker posts blood request for AB_POSITIVE
        mockMvc.perform(post("/requests")
                        .with(csrf())
                        .with(user(e2eSeekerDetails))
                        .param("bloodGroup", "AB_POSITIVE")
                        .param("unitsNeeded", "2")
                        .param("urgency", "CRITICAL")
                        .param("reason", "Cardiac surgery")
                        .param("hospitalName", "Apollo Chennai Hospital")
                        .param("hospitalAddress", "Greams Road, Chennai")
                        .param("requiredBy", LocalDate.now().plusDays(3).toString()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/seeker/dashboard"));

        SeekerRequest postedRequest = seekerRequestRepository
                .findBySeekerIdOrderByCreatedAtDesc(registeredSeeker.getId())
                .get(0);

        // Step 4: Donor sees the PENDING match on their dashboard, and seeker phone is hidden prior to acceptance
        mockMvc.perform(get("/donor/dashboard")
                        .with(user(e2eDonorDetails)))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Apollo Chennai Hospital")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("+919811112222"))));

        mockMvc.perform(get("/requests/" + postedRequest.getId())
                        .with(user(e2eDonorDetails)))
                .andExpect(status().isOk())
                .andExpect(model().attribute("contactRevealed", false))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("+919811112222"))));

        // Step 5: Donor accepts the match
        mockMvc.perform(post("/matches/" + postedRequest.getId() + "/accept")
                        .with(csrf())
                        .with(user(e2eDonorDetails)))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/requests/" + postedRequest.getId()));

        // Step 6: Both Donor and Seeker now see each other's contact info
        // 6a. Donor views request detail -> sees seeker's phone (+919811112222)
        mockMvc.perform(get("/requests/" + postedRequest.getId())
                        .with(user(e2eDonorDetails)))
                .andExpect(status().isOk())
                .andExpect(model().attribute("contactRevealed", true))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("+919811112222")));

        // 6b. Seeker views request detail -> sees donor's name and phone (+919833334444)
        mockMvc.perform(get("/requests/" + postedRequest.getId())
                        .with(user(e2eSeekerDetails)))
                .andExpect(status().isOk())
                .andExpect(model().attribute("contactRevealed", true))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Diya Donor")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("+919833334444")));
    }
}

