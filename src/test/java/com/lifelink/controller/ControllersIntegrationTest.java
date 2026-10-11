package com.lifelink.controller;

import com.lifelink.model.DonorProfile;
import com.lifelink.model.SeekerRequest;
import com.lifelink.model.User;
import com.lifelink.model.enums.BloodGroup;
import com.lifelink.model.enums.Gender;
import com.lifelink.model.enums.RequestStatus;
import com.lifelink.model.enums.Urgency;
import com.lifelink.model.enums.UserType;
import com.lifelink.repository.DonorProfileRepository;
import com.lifelink.repository.MatchRepository;
import com.lifelink.repository.SeekerRequestRepository;
import com.lifelink.repository.UserRepository;
import com.lifelink.security.CustomUserDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ControllersIntegrationTest {

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

    private User seekerUser;
    private User donorUser;
    private CustomUserDetails seekerDetails;
    private CustomUserDetails donorDetails;

    @BeforeEach
    void setUp() {
        seekerUser = userRepository.save(User.builder()
                .username("seeker_ctrl")
                .email("seeker_ctrl@test.com")
                .passwordHash(passwordEncoder.encode("Secret123"))
                .fullName("Seeker Controller")
                .phone("9999900001")
                .city("Mumbai")
                .state("Maharashtra")
                .userType(UserType.SEEKER)
                .build());
        seekerDetails = new CustomUserDetails(seekerUser);

        donorUser = userRepository.save(User.builder()
                .username("donor_ctrl")
                .email("donor_ctrl@test.com")
                .passwordHash(passwordEncoder.encode("Secret123"))
                .fullName("Donor Controller")
                .phone("9999900002")
                .city("Mumbai")
                .state("Maharashtra")
                .userType(UserType.DONOR)
                .build());

        DonorProfile profile = donorProfileRepository.save(DonorProfile.builder()
                .user(donorUser)
                .bloodGroup(BloodGroup.O_POSITIVE)
                .age(29)
                .gender(Gender.MALE)
                .availableToDonate(true)
                .lastDonationDate(LocalDate.now().minusDays(100))
                .build());
        donorUser.setDonorProfile(profile);
        donorDetails = new CustomUserDetails(donorUser);
    }

    @Test
    @DisplayName("HomeController: GET / returns landing page")
    void testLandingPage() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(view().name("index"))
                .andExpect(model().attributeExists("bloodGroups"));
    }

    @Test
    @DisplayName("AuthController: GET /register shows form")
    void testShowRegistrationForm() throws Exception {
        mockMvc.perform(get("/register"))
                .andExpect(status().isOk())
                .andExpect(view().name("auth/register"))
                .andExpect(model().attributeExists("userRegistrationDto"));
    }

    @Test
    @DisplayName("AuthController: POST /register with valid donor data succeeds")
    void testProcessRegistrationSuccess() throws Exception {
        mockMvc.perform(post("/register")
                        .with(csrf())
                        .param("username", "brand_new_donor")
                        .param("email", "brand_new@test.com")
                        .param("password", "Pass1234") // valid: upper, lower, digit, 8 chars
                        .param("fullName", "Brand New Donor")
                        .param("phone", "9876543210")
                        .param("city", "Mumbai")
                        .param("state", "Maharashtra")
                        .param("userType", "DONOR")
                        .param("bloodGroup", "B_POSITIVE")
                        .param("age", "25")
                        .param("gender", "FEMALE"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?registered=true"));
    }

    @Test
    @DisplayName("AuthController: POST /register with weak password fails validation")
    void testProcessRegistrationWeakPassword() throws Exception {
        mockMvc.perform(post("/register")
                        .with(csrf())
                        .param("username", "weak_user")
                        .param("email", "weak@test.com")
                        .param("password", "weak") // fails password policy
                        .param("fullName", "Weak User")
                        .param("phone", "9876543210")
                        .param("city", "Mumbai")
                        .param("state", "Maharashtra")
                        .param("userType", "SEEKER"))
                .andExpect(status().isOk())
                .andExpect(view().name("auth/register"))
                .andExpect(model().hasErrors());
    }

    @Test
    @DisplayName("DashboardController: Dispatches appropriately by user role")
    void testDashboardDispatches() throws Exception {
        mockMvc.perform(get("/dashboard").with(user(seekerDetails)))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/seeker/dashboard"));

        mockMvc.perform(get("/dashboard").with(user(donorDetails)))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/donor/dashboard"));

        mockMvc.perform(get("/donor/dashboard").with(user(donorDetails)))
                .andExpect(status().isOk())
                .andExpect(view().name("donor/dashboard"))
                .andExpect(model().attributeExists("profile", "matches"));
    }

    @Test
    @DisplayName("RequestController: Seeker can create open request")
    void testCreateSeekerRequest() throws Exception {
        mockMvc.perform(post("/requests")
                        .with(csrf())
                        .with(user(seekerDetails))
                        .param("bloodGroup", "O_POSITIVE")
                        .param("unitsNeeded", "2")
                        .param("urgency", "HIGH")
                        .param("hospitalName", "Apex Hospital")
                        .param("hospitalAddress", "Apex Street, Mumbai")
                        .param("requiredBy", LocalDate.now().plusDays(2).toString()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/seeker/dashboard"));
    }

    @Test
    @DisplayName("MatchController: Donor can accept match via canonical accept endpoint")
    void testAcceptMatchEndpoint() throws Exception {
        SeekerRequest request = seekerRequestRepository.save(SeekerRequest.builder()
                .seeker(seekerUser)
                .bloodGroup(BloodGroup.O_POSITIVE)
                .unitsNeeded(1)
                .urgency(Urgency.CRITICAL)
                .hospitalName("Emergency Hospital")
                .hospitalAddress("Emergency Way")
                .requiredBy(LocalDate.now().plusDays(2))
                .status(RequestStatus.OPEN)
                .build());

        mockMvc.perform(post("/matches/" + request.getId() + "/accept")
                        .with(csrf())
                        .with(user(donorDetails)))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/requests/" + request.getId()));
    }

    @Test
    @DisplayName("DonorController: Donor can toggle availability")
    void testToggleDonorAvailability() throws Exception {
        mockMvc.perform(post("/donor/availability")
                        .with(csrf())
                        .with(user(donorDetails))
                        .param("available", "false"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/donor/dashboard"));
    }

    @Test
    @DisplayName("ApiController: GET /api/requests returns paginated JSON without phone numbers")
    void testApiRequestsEndpoint() throws Exception {
        seekerRequestRepository.save(SeekerRequest.builder()
                .seeker(seekerUser)
                .bloodGroup(BloodGroup.O_POSITIVE)
                .unitsNeeded(3)
                .urgency(Urgency.HIGH)
                .hospitalName("Public Clinic")
                .hospitalAddress("Public Road")
                .requiredBy(LocalDate.now().plusDays(3))
                .status(RequestStatus.OPEN)
                .build());

        mockMvc.perform(get("/api/requests")
                        .with(user(donorDetails))
                        .param("page", "0")
                        .param("size", "10")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].hospitalName", is("Public Clinic")))
                .andExpect(jsonPath("$.content[0].phone").doesNotExist());
    }

    @Test
    @DisplayName("ApiController: GET /api/donors returns paginated JSON without phone numbers for ROLE_SEEKER")
    void testApiDonorsEndpoint() throws Exception {
        mockMvc.perform(get("/api/donors")
                        .with(user(seekerDetails))
                        .param("bloodGroup", "O_POSITIVE")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].phone").doesNotExist());
    }

    @Test
    @DisplayName("GlobalExceptionHandler: Non-existent request returns 404 error page")
    void testNotFoundHandling() throws Exception {
        mockMvc.perform(get("/requests/999999").with(user(seekerDetails)))
                .andExpect(status().isNotFound())
                .andExpect(view().name("error/404"));
    }

    @Test
    @DisplayName("AdminController: GET /admin/dashboard returns read-only analytics and recent requests")
    void testAdminDashboardReadOnlyMetrics() throws Exception {
        User adminUser = userRepository.save(User.builder()
                .username("test_super_admin")
                .email("superadmin@test.com")
                .passwordHash(passwordEncoder.encode("Secret123"))
                .fullName("Super Admin")
                .phone("9999900099")
                .city("Mumbai")
                .state("Maharashtra")
                .userType(UserType.ADMIN)
                .isAdmin(true)
                .build());
        CustomUserDetails adminDetails = new CustomUserDetails(adminUser);

        mockMvc.perform(get("/admin/dashboard").with(user(adminDetails)))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/dashboard"))
                .andExpect(model().attributeExists("totalUsers", "donorCount", "seekerCount", "adminCount"))
                .andExpect(model().attributeExists("totalRequests", "openRequests", "fulfilledRequests", "cancelledRequests", "expiredRequests"))
                .andExpect(model().attributeExists("totalMatches", "pendingMatches", "acceptedMatches", "declinedMatches", "expiredMatches"))
                .andExpect(model().attributeExists("recentRequests"));
    }
}
