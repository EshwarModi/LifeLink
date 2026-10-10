package com.lifelink.scheduler;

import com.lifelink.model.Match;
import com.lifelink.model.SeekerRequest;
import com.lifelink.model.User;
import com.lifelink.model.enums.BloodGroup;
import com.lifelink.model.enums.MatchStatus;
import com.lifelink.model.enums.RequestStatus;
import com.lifelink.model.enums.Urgency;
import com.lifelink.model.enums.UserType;
import com.lifelink.repository.MatchRepository;
import com.lifelink.repository.SeekerRequestRepository;
import com.lifelink.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class RequestExpirationSchedulerTest {

    @Autowired
    private RequestExpirationScheduler scheduler;

    @Autowired
    private SeekerRequestRepository seekerRequestRepository;

    @Autowired
    private MatchRepository matchRepository;

    @Autowired
    private UserRepository userRepository;

    private User seeker;
    private User donor;

    @BeforeEach
    void setUp() {
        seeker = userRepository.save(User.builder()
                .username("sched_seeker")
                .email("sched_seeker@test.com")
                .passwordHash("hash")
                .fullName("Scheduler Seeker")
                .phone("1234567890")
                .city("Mumbai")
                .state("Maharashtra")
                .userType(UserType.SEEKER)
                .build());

        donor = userRepository.save(User.builder()
                .username("sched_donor")
                .email("sched_donor@test.com")
                .passwordHash("hash")
                .fullName("Scheduler Donor")
                .phone("0987654321")
                .city("Mumbai")
                .state("Maharashtra")
                .userType(UserType.DONOR)
                .build());
    }

    @Test
    @DisplayName("Scheduler: Flips overdue OPEN requests to EXPIRED and expires their PENDING matches")
    void testOverdueRequestsAndMatchesAreExpired() {
        // 1. Overdue request (requiredBy yesterday) with 1 PENDING match and 1 ACCEPTED match
        SeekerRequest overdueReq = seekerRequestRepository.save(SeekerRequest.builder()
                .seeker(seeker)
                .bloodGroup(BloodGroup.O_POSITIVE)
                .unitsNeeded(2)
                .urgency(Urgency.HIGH)
                .hospitalName("City Clinic")
                .hospitalAddress("Main Road")
                .requiredBy(LocalDate.now().minusDays(1)) // in the past
                .status(RequestStatus.OPEN)
                .build());

        Match pendingMatch = matchRepository.save(Match.builder()
                .request(overdueReq)
                .donor(donor)
                .status(MatchStatus.PENDING)
                .contactShared(false)
                .build());

        // 2. Future request (requiredBy tomorrow) with 1 PENDING match
        SeekerRequest futureReq = seekerRequestRepository.save(SeekerRequest.builder()
                .seeker(seeker)
                .bloodGroup(BloodGroup.O_POSITIVE)
                .unitsNeeded(1)
                .urgency(Urgency.LOW)
                .hospitalName("Metro Hospital")
                .hospitalAddress("Metro Way")
                .requiredBy(LocalDate.now().plusDays(2)) // in the future
                .status(RequestStatus.OPEN)
                .build());

        User donor2 = userRepository.save(User.builder()
                .username("sched_donor2")
                .email("sched_donor2@test.com")
                .passwordHash("hash")
                .fullName("Donor Two")
                .phone("1122334455")
                .city("Mumbai")
                .state("Maharashtra")
                .userType(UserType.DONOR)
                .build());

        Match futurePendingMatch = matchRepository.save(Match.builder()
                .request(futureReq)
                .donor(donor2)
                .status(MatchStatus.PENDING)
                .contactShared(false)
                .build());

        // Execute scheduled job
        int expiredCount = scheduler.expireOverdueRequests();

        assertThat(expiredCount).isEqualTo(1);

        // Verify overdue request flipped to EXPIRED
        SeekerRequest reloadedOverdue = seekerRequestRepository.findById(overdueReq.getId()).orElseThrow();
        assertThat(reloadedOverdue.getStatus()).isEqualTo(RequestStatus.EXPIRED);

        // Verify overdue request's PENDING match flipped to EXPIRED
        Match reloadedPendingMatch = matchRepository.findById(pendingMatch.getId()).orElseThrow();
        assertThat(reloadedPendingMatch.getStatus()).isEqualTo(MatchStatus.EXPIRED);

        // Verify future request remains OPEN
        SeekerRequest reloadedFuture = seekerRequestRepository.findById(futureReq.getId()).orElseThrow();
        assertThat(reloadedFuture.getStatus()).isEqualTo(RequestStatus.OPEN);

        // Verify future request's PENDING match remains PENDING
        Match reloadedFuturePending = matchRepository.findById(futurePendingMatch.getId()).orElseThrow();
        assertThat(reloadedFuturePending.getStatus()).isEqualTo(MatchStatus.PENDING);
    }
}
