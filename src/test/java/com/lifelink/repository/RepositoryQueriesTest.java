package com.lifelink.repository;

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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class RepositoryQueriesTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DonorProfileRepository donorProfileRepository;

    @Autowired
    private SeekerRequestRepository seekerRequestRepository;

    @Autowired
    private MatchRepository matchRepository;

    private User seeker;
    private User donorCityEligible;
    private User donorCityUnavailable;
    private User donorCityRecentDonation;
    private User donorStateEligible;
    private User donorBroadEligible;

    @BeforeEach
    void setUp() {
        LocalDate cutoffDate = LocalDate.now().minusDays(90);

        // Seeker in Mumbai, Maharashtra
        seeker = createUser("seeker1", "seeker1@mail.com", "Mumbai", "Maharashtra", UserType.SEEKER);

        // Donor 1: Mumbai, Maharashtra (Eligible: O+, available, never donated)
        donorCityEligible = createDonorUser("donor1", "donor1@mail.com", "Mumbai", "Maharashtra",
                BloodGroup.O_POSITIVE, true, null);

        // Donor 2: Mumbai, Maharashtra (Ineligible: O+, unavailable)
        donorCityUnavailable = createDonorUser("donor2", "donor2@mail.com", "Mumbai", "Maharashtra",
                BloodGroup.O_POSITIVE, false, null);

        // Donor 3: Mumbai, Maharashtra (Ineligible: O+, donated 30 days ago < 90)
        donorCityRecentDonation = createDonorUser("donor3", "donor3@mail.com", "Mumbai", "Maharashtra",
                BloodGroup.O_POSITIVE, true, LocalDate.now().minusDays(30));

        // Donor 4: Pune, Maharashtra (Eligible Tier 2: O+, same state, different city, donated 100 days ago)
        donorStateEligible = createDonorUser("donor4", "donor4@mail.com", "Pune", "Maharashtra",
                BloodGroup.O_POSITIVE, true, LocalDate.now().minusDays(100));

        // Donor 5: Bangalore, Karnataka (Eligible Tier 3: O+, different state, available)
        donorBroadEligible = createDonorUser("donor5", "donor5@mail.com", "Bangalore", "Karnataka",
                BloodGroup.O_POSITIVE, true, null);
    }

    private User createUser(String username, String email, String city, String state, UserType userType) {
        User user = User.builder()
                .username(username)
                .email(email)
                .passwordHash("hashedPass123")
                .fullName(username + " FullName")
                .phone("9876543210")
                .city(city)
                .state(state)
                .userType(userType)
                .build();
        return userRepository.save(user);
    }

    private User createDonorUser(String username, String email, String city, String state,
                                BloodGroup bloodGroup, boolean available, LocalDate lastDonationDate) {
        User user = createUser(username, email, city, state, UserType.DONOR);
        DonorProfile profile = DonorProfile.builder()
                .user(user)
                .bloodGroup(bloodGroup)
                .age(25)
                .gender(Gender.MALE)
                .availableToDonate(available)
                .lastDonationDate(lastDonationDate)
                .medicalConditions("None")
                .build();
        donorProfileRepository.save(profile);
        user.setDonorProfile(profile);
        return user;
    }

    @Test
    @DisplayName("Tier 1: Finds only eligible donors with exact blood group and same city")
    void testTier1MatchingQuery() {
        LocalDate cutoff = LocalDate.now().minusDays(90);
        List<User> tier1Matches = userRepository.findEligibleDonorsByBloodGroupAndCity(
                BloodGroup.O_POSITIVE, "mumbai", cutoff, seeker.getId()
        );

        assertThat(tier1Matches).hasSize(1);
        assertThat(tier1Matches.get(0).getUsername()).isEqualTo("donor1");
    }

    @Test
    @DisplayName("Tier 2: Finds eligible donors in same state excluding same city")
    void testTier2MatchingQuery() {
        LocalDate cutoff = LocalDate.now().minusDays(90);
        List<User> tier2Matches = userRepository.findEligibleDonorsByBloodGroupAndStateExcludingCity(
                BloodGroup.O_POSITIVE, "Maharashtra", "Mumbai", cutoff, seeker.getId()
        );

        assertThat(tier2Matches).hasSize(1);
        assertThat(tier2Matches.get(0).getUsername()).isEqualTo("donor4");
    }

    @Test
    @DisplayName("Tier 3: Finds eligible donors broad nationwide excluding same state")
    void testTier3MatchingQuery() {
        LocalDate cutoff = LocalDate.now().minusDays(90);
        List<User> tier3Matches = userRepository.findEligibleDonorsByBloodGroupBroadExcludingState(
                BloodGroup.O_POSITIVE, "Maharashtra", cutoff, seeker.getId()
        );

        assertThat(tier3Matches).hasSize(1);
        assertThat(tier3Matches.get(0).getUsername()).isEqualTo("donor5");
    }

    @Test
    @DisplayName("SeekerRequestRepository: Paginated open requests by blood group")
    void testPaginatedOpenRequestsByBloodGroup() {
        // Save 2 requests
        SeekerRequest req1 = SeekerRequest.builder()
                .seeker(seeker)
                .bloodGroup(BloodGroup.O_POSITIVE)
                .unitsNeeded(2)
                .urgency(Urgency.HIGH)
                .hospitalName("Hospital A")
                .hospitalAddress("Addr A")
                .requiredBy(LocalDate.now().plusDays(3))
                .status(RequestStatus.OPEN)
                .build();
        SeekerRequest req2 = SeekerRequest.builder()
                .seeker(seeker)
                .bloodGroup(BloodGroup.A_POSITIVE)
                .unitsNeeded(1)
                .urgency(Urgency.LOW)
                .hospitalName("Hospital B")
                .hospitalAddress("Addr B")
                .requiredBy(LocalDate.now().plusDays(5))
                .status(RequestStatus.OPEN)
                .build();
        seekerRequestRepository.save(req1);
        seekerRequestRepository.save(req2);

        Page<SeekerRequest> page = seekerRequestRepository.findByBloodGroupAndStatus(
                BloodGroup.O_POSITIVE, RequestStatus.OPEN, PageRequest.of(0, 10, Sort.by("createdAt").descending())
        );

        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent().get(0).getHospitalName()).isEqualTo("Hospital A");
    }

    @Test
    @DisplayName("SeekerRequestRepository: Query overdue open requests for expiration")
    void testOverdueRequestsQuery() {
        SeekerRequest overdueReq = SeekerRequest.builder()
                .seeker(seeker)
                .bloodGroup(BloodGroup.O_POSITIVE)
                .unitsNeeded(1)
                .urgency(Urgency.MEDIUM)
                .hospitalName("Hospital C")
                .hospitalAddress("Addr C")
                .requiredBy(LocalDate.now().minusDays(1)) // in the past
                .status(RequestStatus.OPEN)
                .build();
        seekerRequestRepository.save(overdueReq);

        List<SeekerRequest> overdue = seekerRequestRepository.findByStatusAndRequiredByBefore(
                RequestStatus.OPEN, LocalDate.now()
        );

        assertThat(overdue).extracting(SeekerRequest::getId).contains(overdueReq.getId());
    }

    @Test
    @DisplayName("MatchRepository: Find by request and donor, and matches by donor")
    void testMatchRepositoryQueries() {
        SeekerRequest request = SeekerRequest.builder()
                .seeker(seeker)
                .bloodGroup(BloodGroup.O_POSITIVE)
                .unitsNeeded(1)
                .urgency(Urgency.CRITICAL)
                .hospitalName("Hospital D")
                .hospitalAddress("Addr D")
                .requiredBy(LocalDate.now().plusDays(1))
                .status(RequestStatus.OPEN)
                .build();
        seekerRequestRepository.save(request);

        Match match = Match.builder()
                .request(request)
                .donor(donorCityEligible)
                .status(MatchStatus.PENDING)
                .contactShared(false)
                .build();
        matchRepository.save(match);

        // Test find by request and donor ID
        Optional<Match> foundMatch = matchRepository.findByRequestIdAndDonorId(request.getId(), donorCityEligible.getId());
        assertThat(foundMatch).isPresent();
        assertThat(foundMatch.get().getStatus()).isEqualTo(MatchStatus.PENDING);

        // Test matches by donor ID
        List<Match> donorMatches = matchRepository.findByDonorIdOrderByMatchedAtDesc(donorCityEligible.getId());
        assertThat(donorMatches).hasSize(1);

        // Test find by donor and status (used when toggling unavailable)
        List<Match> pendingMatches = matchRepository.findByDonorIdAndStatus(donorCityEligible.getId(), MatchStatus.PENDING);
        assertThat(pendingMatches).hasSize(1);
    }
}
