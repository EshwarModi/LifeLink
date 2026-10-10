package com.lifelink.service;

import com.lifelink.dto.DonorProfileDto;
import com.lifelink.dto.SeekerRequestDto;
import com.lifelink.dto.UserRegistrationDto;
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
import com.lifelink.service.exception.DuplicateResourceException;
import com.lifelink.service.exception.UnauthorizedActionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class LifeLinkServicesTest {

    @Autowired
    private UserService userService;

    @Autowired
    private DonorService donorService;

    @Autowired
    private SeekerRequestService seekerRequestService;

    @Autowired
    private MatchingService matchingService;

    @Autowired
    private NotificationService notificationService;

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

    @MockBean
    private JavaMailSender javaMailSender;

    private User seekerUser;
    private User donorUser1;
    private User donorUser2;

    @BeforeEach
    void setUp() {
        seekerUser = userRepository.save(User.builder()
                .username("test_seeker")
                .email("seeker@test.com")
                .passwordHash(passwordEncoder.encode("Secret123"))
                .fullName("Seeker Test")
                .phone("9999999991")
                .city("Mumbai")
                .state("Maharashtra")
                .userType(UserType.SEEKER)
                .build());

        donorUser1 = userRepository.save(User.builder()
                .username("donor_mumbai")
                .email("donor_mumbai@test.com")
                .passwordHash(passwordEncoder.encode("Secret123"))
                .fullName("Donor Mumbai")
                .phone("8888888881")
                .city("Mumbai")
                .state("Maharashtra")
                .userType(UserType.DONOR)
                .build());

        DonorProfile p1 = donorProfileRepository.save(DonorProfile.builder()
                .user(donorUser1)
                .bloodGroup(BloodGroup.O_POSITIVE)
                .age(30)
                .gender(Gender.MALE)
                .availableToDonate(true)
                .lastDonationDate(LocalDate.now().minusDays(100))
                .build());
        donorUser1.setDonorProfile(p1);

        donorUser2 = userRepository.save(User.builder()
                .username("donor_pune")
                .email("donor_pune@test.com")
                .passwordHash(passwordEncoder.encode("Secret123"))
                .fullName("Donor Pune")
                .phone("8888888882")
                .city("Pune")
                .state("Maharashtra")
                .userType(UserType.DONOR)
                .build());

        DonorProfile p2 = donorProfileRepository.save(DonorProfile.builder()
                .user(donorUser2)
                .bloodGroup(BloodGroup.O_POSITIVE)
                .age(28)
                .gender(Gender.FEMALE)
                .availableToDonate(true)
                .lastDonationDate(null)
                .build());
        donorUser2.setDonorProfile(p2);
    }

    // =========================================================================
    // 1. UserService Tests
    // =========================================================================

    @Test
    @DisplayName("UserService: Prevents duplicate username registration")
    void testRegisterDuplicateUsername() {
        UserRegistrationDto dto = UserRegistrationDto.builder()
                .username("test_seeker")
                .email("different@test.com")
                .password("Password123")
                .fullName("Duplicate User")
                .phone("1234567890")
                .city("Delhi")
                .state("Delhi")
                .userType(UserType.SEEKER)
                .build();

        assertThatThrownBy(() -> userService.registerUser(dto))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("already taken");
    }

    @Test
    @DisplayName("UserService: Prevents duplicate email registration")
    void testRegisterDuplicateEmail() {
        UserRegistrationDto dto = UserRegistrationDto.builder()
                .username("unique_user")
                .email("seeker@test.com")
                .password("Password123")
                .fullName("Duplicate Email")
                .phone("1234567890")
                .city("Delhi")
                .state("Delhi")
                .userType(UserType.SEEKER)
                .build();

        assertThatThrownBy(() -> userService.registerUser(dto))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("already registered");
    }

    @Test
    @DisplayName("UserService: Successfully registers donor with encrypted password and initial profile")
    void testRegisterDonorSuccess() {
        UserRegistrationDto dto = UserRegistrationDto.builder()
                .username("new_donor")
                .email("newdonor@test.com")
                .password("StrongPass1")
                .fullName("New Donor")
                .phone("9876543210")
                .city("Nagpur")
                .state("Maharashtra")
                .userType(UserType.DONOR)
                .bloodGroup(BloodGroup.A_POSITIVE)
                .age(25)
                .gender(Gender.MALE)
                .build();

        User registered = userService.registerUser(dto);
        assertThat(registered.getId()).isNotNull();
        assertThat(passwordEncoder.matches("StrongPass1", registered.getPasswordHash())).isTrue();
        assertThat(registered.getDonorProfile()).isNotNull();
        assertThat(registered.getDonorProfile().getBloodGroup()).isEqualTo(BloodGroup.A_POSITIVE);
        assertThat(registered.getDonorProfile().isAvailableToDonate()).isTrue();
    }

    // =========================================================================
    // 2. DonorService Tests
    // =========================================================================

    @Test
    @DisplayName("DonorService: 90-day eligibility rule works correctly")
    void testDonorEligibilityRule() {
        DonorProfile eligible100Days = DonorProfile.builder()
                .availableToDonate(true)
                .lastDonationDate(LocalDate.now().minusDays(100))
                .build();
        assertThat(donorService.isDonorEligible(eligible100Days)).isTrue();

        DonorProfile eligibleNeverDonated = DonorProfile.builder()
                .availableToDonate(true)
                .lastDonationDate(null)
                .build();
        assertThat(donorService.isDonorEligible(eligibleNeverDonated)).isTrue();

        DonorProfile ineligible30Days = DonorProfile.builder()
                .availableToDonate(true)
                .lastDonationDate(LocalDate.now().minusDays(30))
                .build();
        assertThat(donorService.isDonorEligible(ineligible30Days)).isFalse();

        DonorProfile ineligibleUnavailable = DonorProfile.builder()
                .availableToDonate(false)
                .lastDonationDate(null)
                .build();
        assertThat(donorService.isDonorEligible(ineligibleUnavailable)).isFalse();
    }

    @Test
    @DisplayName("DonorService: Availability toggle cascades PENDING matches to EXPIRED (Design Decision #10)")
    void testAvailabilityToggleCascadesPendingMatches() {
        SeekerRequest request = seekerRequestRepository.save(SeekerRequest.builder()
                .seeker(seekerUser)
                .bloodGroup(BloodGroup.O_POSITIVE)
                .unitsNeeded(2)
                .urgency(Urgency.HIGH)
                .hospitalName("Hospital X")
                .hospitalAddress("Addr X")
                .requiredBy(LocalDate.now().plusDays(2))
                .status(RequestStatus.OPEN)
                .build());

        Match pendingMatch = matchRepository.save(Match.builder()
                .request(request)
                .donor(donorUser1)
                .status(MatchStatus.PENDING)
                .contactShared(false)
                .build());

        // Toggle donor1 availability to false
        donorService.toggleAvailability(donorUser1.getId(), false);

        Match reloaded = matchRepository.findById(pendingMatch.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(MatchStatus.EXPIRED);
    }

    // =========================================================================
    // 3. SeekerRequestService Tests
    // =========================================================================

    @Test
    @DisplayName("SeekerRequestService: Create request enforces future required-by date (Design Decision #6)")
    void testCreateRequestRequiresFutureDate() {
        SeekerRequestDto pastDto = SeekerRequestDto.builder()
                .bloodGroup(BloodGroup.O_POSITIVE)
                .unitsNeeded(2)
                .urgency(Urgency.HIGH)
                .hospitalName("Hospital Y")
                .hospitalAddress("Addr Y")
                .requiredBy(LocalDate.now().minusDays(1)) // past date
                .build();

        assertThatThrownBy(() -> seekerRequestService.createRequest(seekerUser.getId(), pastDto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("future");
    }

    @Test
    @DisplayName("SeekerRequestService: Edit request enforces identical future required-by validation (Design Decision #6)")
    void testEditRequestRequiresIdenticalFutureDate() {
        SeekerRequest request = seekerRequestRepository.save(SeekerRequest.builder()
                .seeker(seekerUser)
                .bloodGroup(BloodGroup.O_POSITIVE)
                .unitsNeeded(2)
                .urgency(Urgency.HIGH)
                .hospitalName("Hospital Z")
                .hospitalAddress("Addr Z")
                .requiredBy(LocalDate.now().plusDays(2))
                .status(RequestStatus.OPEN)
                .build());

        SeekerRequestDto editPastDto = SeekerRequestDto.builder()
                .bloodGroup(BloodGroup.O_POSITIVE)
                .unitsNeeded(2)
                .urgency(Urgency.HIGH)
                .hospitalName("Hospital Z")
                .hospitalAddress("Addr Z")
                .requiredBy(LocalDate.now().minusDays(1)) // invalid past date
                .build();

        assertThatThrownBy(() -> seekerRequestService.editRequest(seekerUser.getId(), request.getId(), editPastDto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("future");
    }

    @Test
    @DisplayName("SeekerRequestService: Non-owner cannot edit request")
    void testEditRequestOwnershipCheck() {
        SeekerRequest request = seekerRequestRepository.save(SeekerRequest.builder()
                .seeker(seekerUser)
                .bloodGroup(BloodGroup.O_POSITIVE)
                .unitsNeeded(2)
                .urgency(Urgency.HIGH)
                .hospitalName("Hospital Z")
                .hospitalAddress("Addr Z")
                .requiredBy(LocalDate.now().plusDays(2))
                .status(RequestStatus.OPEN)
                .build());

        SeekerRequestDto validDto = SeekerRequestDto.builder()
                .bloodGroup(BloodGroup.O_POSITIVE)
                .unitsNeeded(3)
                .urgency(Urgency.CRITICAL)
                .hospitalName("Hospital Z")
                .hospitalAddress("Addr Z")
                .requiredBy(LocalDate.now().plusDays(3))
                .build();

        assertThatThrownBy(() -> seekerRequestService.editRequest(donorUser1.getId(), request.getId(), validDto))
                .isInstanceOf(UnauthorizedActionException.class);
    }

    @Test
    @DisplayName("SeekerRequestService: Mark fulfilled and mark cancelled")
    void testMarkFulfilledAndCancelled() {
        SeekerRequest req1 = seekerRequestRepository.save(SeekerRequest.builder()
                .seeker(seekerUser)
                .bloodGroup(BloodGroup.O_POSITIVE)
                .unitsNeeded(1)
                .urgency(Urgency.LOW)
                .hospitalName("Hospital H")
                .hospitalAddress("Addr H")
                .requiredBy(LocalDate.now().plusDays(3))
                .status(RequestStatus.OPEN)
                .build());

        SeekerRequest fulfilled = seekerRequestService.markFulfilled(seekerUser.getId(), req1.getId());
        assertThat(fulfilled.getStatus()).isEqualTo(RequestStatus.FULFILLED);

        SeekerRequest req2 = seekerRequestRepository.save(SeekerRequest.builder()
                .seeker(seekerUser)
                .bloodGroup(BloodGroup.O_POSITIVE)
                .unitsNeeded(1)
                .urgency(Urgency.LOW)
                .hospitalName("Hospital H")
                .hospitalAddress("Addr H")
                .requiredBy(LocalDate.now().plusDays(3))
                .status(RequestStatus.OPEN)
                .build());

        SeekerRequest cancelled = seekerRequestService.markCancelled(seekerUser.getId(), req2.getId());
        assertThat(cancelled.getStatus()).isEqualTo(RequestStatus.CANCELLED);
    }

    // =========================================================================
    // 4. MatchingService Tests
    // =========================================================================

    @Test
    @DisplayName("MatchingService: Three-tier location-aware matching (City -> State -> Broad)")
    void testThreeTierMatching() {
        SeekerRequest request = seekerRequestRepository.save(SeekerRequest.builder()
                .seeker(seekerUser) // Mumbai, Maharashtra
                .bloodGroup(BloodGroup.O_POSITIVE)
                .unitsNeeded(2)
                .urgency(Urgency.MEDIUM)
                .hospitalName("Hospital M")
                .hospitalAddress("Addr M")
                .requiredBy(LocalDate.now().plusDays(2))
                .status(RequestStatus.OPEN)
                .build());

        List<Match> matches = matchingService.findAndCreateMatches(request);

        // Expect Tier 1 (donorUser1 in Mumbai) + Tier 2 (donorUser2 in Pune)
        assertThat(matches).hasSize(2);
        assertThat(matches).extracting(m -> m.getDonor().getUsername())
                .containsExactlyInAnyOrder("donor_mumbai", "donor_pune");
        assertThat(matches.get(0).getStatus()).isEqualTo(MatchStatus.PENDING);
        assertThat(matches.get(0).isContactShared()).isFalse();
    }

    @Test
    @DisplayName("MatchingService: Canonical acceptMatch reveals contact details ONLY after acceptance (Design Decision #5)")
    void testAcceptMatchWithOwnershipCheckAndContactSharing() {
        SeekerRequest request = seekerRequestRepository.save(SeekerRequest.builder()
                .seeker(seekerUser)
                .bloodGroup(BloodGroup.O_POSITIVE)
                .unitsNeeded(1)
                .urgency(Urgency.CRITICAL)
                .hospitalName("Hospital C")
                .hospitalAddress("Addr C")
                .requiredBy(LocalDate.now().plusDays(1))
                .status(RequestStatus.OPEN)
                .build());

        // Upsert acceptance by eligible donor1
        Match acceptedMatch = matchingService.acceptMatch(request.getId(), donorUser1.getId());

        assertThat(acceptedMatch.getStatus()).isEqualTo(MatchStatus.ACCEPTED);
        assertThat(acceptedMatch.isContactShared()).isTrue();
        assertThat(acceptedMatch.getDonor().getId()).isEqualTo(donorUser1.getId());
    }

    @Test
    @DisplayName("MatchingService: Ineligible donor cannot accept match")
    void testIneligibleDonorCannotAccept() {
        // Make donor1 unavailable
        donorUser1.getDonorProfile().setAvailableToDonate(false);
        donorProfileRepository.save(donorUser1.getDonorProfile());

        SeekerRequest request = seekerRequestRepository.save(SeekerRequest.builder()
                .seeker(seekerUser)
                .bloodGroup(BloodGroup.O_POSITIVE)
                .unitsNeeded(1)
                .urgency(Urgency.CRITICAL)
                .hospitalName("Hospital C")
                .hospitalAddress("Addr C")
                .requiredBy(LocalDate.now().plusDays(1))
                .status(RequestStatus.OPEN)
                .build());

        assertThatThrownBy(() -> matchingService.acceptMatch(request.getId(), donorUser1.getId()))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("ineligible");
    }

    // =========================================================================
    // 5. NotificationService Tests
    // =========================================================================

    @Test
    @DisplayName("NotificationService: Mail delivery failure does not disrupt workflow (Design Decision #12)")
    void testNotificationServiceFailureTolerance() {
        doThrow(new RuntimeException("SMTP connection timeout")).when(javaMailSender).send(any(SimpleMailMessage.class));

        SeekerRequest request = SeekerRequest.builder()
                .id(100L)
                .seeker(seekerUser)
                .bloodGroup(BloodGroup.O_POSITIVE)
                .unitsNeeded(1)
                .urgency(Urgency.HIGH)
                .hospitalName("General Hospital")
                .hospitalAddress("Central Road")
                .requiredBy(LocalDate.now().plusDays(2))
                .status(RequestStatus.OPEN)
                .build();

        // Must execute gracefully without throwing an exception
        notificationService.sendMatchNotification(donorUser1, request);
    }
}
