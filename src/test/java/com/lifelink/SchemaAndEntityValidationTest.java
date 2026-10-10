package com.lifelink;

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
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SchemaAndEntityValidationTest {

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("Persist and query User, DonorProfile, SeekerRequest, and Match entities successfully")
    void testEntitiesAndSchemaLifecycle() {
        // 1. Create and persist Seeker User
        User seeker = User.builder()
                .username("seeker_john")
                .email("john@example.com")
                .passwordHash("$2a$12$e8x5a3J9aN/4fP5F91jGhuqK9mJ7A.w6xQ9pT")
                .fullName("John Seeker")
                .phone("9876543210")
                .city("Mumbai")
                .state("Maharashtra")
                .userType(UserType.SEEKER)
                .isAdmin(false)
                .build();
        entityManager.persist(seeker);

        // 2. Create and persist Donor User with 1:1 DonorProfile
        User donor = User.builder()
                .username("donor_alice")
                .email("alice@example.com")
                .passwordHash("$2a$12$e8x5a3J9aN/4fP5F91jGhuqK9mJ7A.w6xQ9pT")
                .fullName("Alice Donor")
                .phone("9123456780")
                .city("Mumbai")
                .state("Maharashtra")
                .userType(UserType.DONOR)
                .isAdmin(false)
                .build();
        entityManager.persist(donor);

        DonorProfile profile = DonorProfile.builder()
                .user(donor)
                .bloodGroup(BloodGroup.O_POSITIVE)
                .age(28)
                .gender(Gender.FEMALE)
                .availableToDonate(true)
                .lastDonationDate(LocalDate.now().minusDays(100))
                .medicalConditions("None")
                .build();
        entityManager.persist(profile);
        donor.setDonorProfile(profile);

        // 3. Create and persist SeekerRequest
        SeekerRequest request = SeekerRequest.builder()
                .seeker(seeker)
                .bloodGroup(BloodGroup.O_POSITIVE)
                .unitsNeeded(2)
                .urgency(Urgency.HIGH)
                .reason("Scheduled surgical procedure")
                .hospitalName("City Care Hospital")
                .hospitalAddress("123 Health Ave, Mumbai")
                .requiredBy(LocalDate.now().plusDays(2))
                .status(RequestStatus.OPEN)
                .build();
        entityManager.persist(request);

        // 4. Create and persist Match
        Match match = Match.builder()
                .request(request)
                .donor(donor)
                .status(MatchStatus.PENDING)
                .contactShared(false)
                .build();
        entityManager.persist(match);

        entityManager.flush();
        entityManager.clear();

        // 5. Query and verify assertions
        Match retrievedMatch = entityManager.find(Match.class, match.getId());
        assertThat(retrievedMatch).isNotNull();
        assertThat(retrievedMatch.getStatus()).isEqualTo(MatchStatus.PENDING);
        assertThat(retrievedMatch.isContactShared()).isFalse();
        assertThat(retrievedMatch.getMatchedAt()).isNotNull();

        assertThat(retrievedMatch.getRequest().getBloodGroup()).isEqualTo(BloodGroup.O_POSITIVE);
        assertThat(retrievedMatch.getRequest().getSeeker().getUsername()).isEqualTo("seeker_john");

        User retrievedDonor = entityManager.find(User.class, donor.getId());
        assertThat(retrievedDonor).isNotNull();
        assertThat(retrievedDonor.getDonorProfile()).isNotNull();
        assertThat(retrievedDonor.getDonorProfile().getBloodGroup()).isEqualTo(BloodGroup.O_POSITIVE);
        assertThat(retrievedDonor.getDonorProfile().isAvailableToDonate()).isTrue();
    }
}
