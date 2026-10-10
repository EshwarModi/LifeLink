package com.lifelink.service;

import com.lifelink.model.DonorProfile;
import com.lifelink.model.Match;
import com.lifelink.model.SeekerRequest;
import com.lifelink.model.User;
import com.lifelink.model.enums.BloodGroup;
import com.lifelink.model.enums.MatchStatus;
import com.lifelink.model.enums.RequestStatus;
import com.lifelink.repository.MatchRepository;
import com.lifelink.repository.SeekerRequestRepository;
import com.lifelink.repository.UserRepository;
import com.lifelink.service.exception.InvalidRequestStateException;
import com.lifelink.service.exception.ResourceNotFoundException;
import com.lifelink.service.exception.UnauthorizedActionException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Matching engine and match lifecycle service for LifeLink.
 *
 * <p><h3>Location-Aware Three-Tier Matching Algorithm (Design Decision #8):</h3>
 * When a blood request is posted or matched, the system searches for available, eligible donors
 * having the exact matching blood group (Design Decision #15) in three progressive geographical tiers:
 * <ol>
 *   <li><b>Tier 1 (City-Level):</b> Search for eligible donors within the exact same city as the seeker.</li>
 *   <li><b>Tier 2 (State-Level):</b> If Tier 1 produces fewer than {@value #TARGET_MATCH_THRESHOLD} donors,
 *       expand the search to the same state (excluding donors already matched from Tier 1).</li>
 *   <li><b>Tier 3 (Nationwide/Broad):</b> If the combined match count remains under {@value #TARGET_MATCH_THRESHOLD},
 *       expand nationwide to any eligible donor with the requested blood group.</li>
 * </ol>
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MatchingService {

    /**
     * Threshold of donor candidates targeted per request before stopping tier expansion.
     */
    public static final int TARGET_MATCH_THRESHOLD = 3;

    private final UserRepository userRepository;
    private final SeekerRequestRepository seekerRequestRepository;
    private final MatchRepository matchRepository;
    private final NotificationService notificationService;
    private final DonorService donorService;

    /**
     * Executes the three-tier matching workflow for an open seeker request.
     * Persists new PENDING matches and sends notification emails without breaking on delivery errors.
     *
     * @param request the open SeekerRequest to match
     * @return list of newly created or existing matches
     */
    @Transactional
    public List<Match> findAndCreateMatches(SeekerRequest request) {
        if (request.getStatus() != RequestStatus.OPEN) {
            log.warn("Cannot match request ID {}: status is not OPEN (current: {})", request.getId(), request.getStatus());
            return List.of();
        }

        LocalDate cutoffDate = LocalDate.now().minusDays(DonorService.ELIGIBILITY_INTERVAL_DAYS);
        BloodGroup bloodGroup = request.getBloodGroup();
        String city = request.getSeeker().getCity();
        String state = request.getSeeker().getState();
        Long seekerId = request.getSeeker().getId();

        List<User> matchedDonors = new ArrayList<>();
        Set<Long> pickedDonorIds = new HashSet<>();

        // Tier 1: Same blood group + exact same city
        List<User> tier1Donors = userRepository.findEligibleDonorsByBloodGroupAndCity(
                bloodGroup, city, cutoffDate, seekerId
        );
        for (User u : tier1Donors) {
            if (pickedDonorIds.add(u.getId())) {
                matchedDonors.add(u);
            }
        }
        log.info("Tier 1 (City: {}) found {} eligible donors for request ID {}", city, tier1Donors.size(), request.getId());

        // Tier 2: If under TARGET_MATCH_THRESHOLD, expand to same state
        if (matchedDonors.size() < TARGET_MATCH_THRESHOLD) {
            List<User> tier2Donors = userRepository.findEligibleDonorsByBloodGroupAndStateExcludingCity(
                    bloodGroup, state, city, cutoffDate, seekerId
            );
            for (User u : tier2Donors) {
                if (pickedDonorIds.add(u.getId())) {
                    matchedDonors.add(u);
                    if (matchedDonors.size() >= TARGET_MATCH_THRESHOLD) {
                        break;
                    }
                }
            }
            log.info("Tier 2 (State: {}) brought total donors to {} for request ID {}", state, matchedDonors.size(), request.getId());
        }

        // Tier 3: If still under TARGET_MATCH_THRESHOLD, expand nationwide
        if (matchedDonors.size() < TARGET_MATCH_THRESHOLD) {
            List<User> tier3Donors = userRepository.findEligibleDonorsByBloodGroupBroadExcludingState(
                    bloodGroup, state, cutoffDate, seekerId
            );
            for (User u : tier3Donors) {
                if (pickedDonorIds.add(u.getId())) {
                    matchedDonors.add(u);
                    if (matchedDonors.size() >= TARGET_MATCH_THRESHOLD) {
                        break;
                    }
                }
            }
            log.info("Tier 3 (Nationwide) brought total donors to {} for request ID {}", matchedDonors.size(), request.getId());
        }

        // Persist matches and trigger email notifications
        List<Match> createdMatches = new ArrayList<>();
        for (User donor : matchedDonors) {
            if (!matchRepository.existsByRequestIdAndDonorId(request.getId(), donor.getId())) {
                Match match = Match.builder()
                        .request(request)
                        .donor(donor)
                        .status(MatchStatus.PENDING)
                        .contactShared(false)
                        .matchedAt(LocalDateTime.now())
                        .build();

                Match savedMatch = matchRepository.save(match);
                createdMatches.add(savedMatch);

                // Send decoupled, fail-safe notification (Design Decision #12)
                notificationService.sendMatchNotification(donor, request);
            }
        }

        return createdMatches;
    }

    /**
     * Canonical match acceptance endpoint (Design Decision #5).
     *
     * <p>Enforces strict donor ownership: a donor can ONLY accept matches for themselves.
     * Supports upsert semantics: if a match record does not yet exist (e.g. donor browsed
     * an open request directory and clicked "I Can Help"), the match is created and accepted in one atomic transaction.
     * Once accepted, {@code contactShared} is set to true, disclosing phone numbers to both parties.</p>
     *
     * @param requestId ID of the seeker request
     * @param donorUserId ID of the authenticated donor user
     * @return the accepted Match entity with contactShared = true
     */
    @Transactional
    public Match acceptMatch(Long requestId, Long donorUserId) {
        SeekerRequest request = seekerRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Request not found with ID: " + requestId));

        if (request.getStatus() != RequestStatus.OPEN) {
            throw new InvalidRequestStateException("Cannot accept match for request that is " + request.getStatus());
        }

        User donor = userRepository.findById(donorUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Donor not found with ID: " + donorUserId));

        // Prevent seeker from accepting their own request as a donor
        if (request.getSeeker().getId().equals(donorUserId)) {
            throw new UnauthorizedActionException("Seekers cannot accept their own requests as donors.");
        }

        // Verify donor eligibility
        DonorProfile profile = donor.getDonorProfile();
        if (profile == null || !donorService.isDonorEligible(profile)) {
            throw new InvalidRequestStateException("Donor is currently ineligible to donate (unavailable or interval < 90 days).");
        }

        // Exact blood group check (Design Decision #15)
        if (profile.getBloodGroup() != request.getBloodGroup()) {
            throw new InvalidRequestStateException("Donor blood group (" + profile.getBloodGroup() +
                    ") does not match request (" + request.getBloodGroup() + ").");
        }

        // Check if a match record already exists (e.g., from auto-matching)
        Optional<Match> existingMatchOpt = matchRepository.findByRequestIdAndDonorId(requestId, donorUserId);

        Match match;
        if (existingMatchOpt.isPresent()) {
            match = existingMatchOpt.get();

            // Ownership check (Design Decision #5)
            if (!match.getDonor().getId().equals(donorUserId)) {
                throw new UnauthorizedActionException("You are not authorized to accept a match for another donor.");
            }

            match.setStatus(MatchStatus.ACCEPTED);
            match.setContactShared(true);
        } else {
            // Upsert: Create brand new accepted match
            match = Match.builder()
                    .request(request)
                    .donor(donor)
                    .status(MatchStatus.ACCEPTED)
                    .contactShared(true)
                    .matchedAt(LocalDateTime.now())
                    .build();
        }

        Match savedMatch = matchRepository.save(match);
        log.info("[SECURITY_AUDIT] event=MATCH_ACCEPTED donorId={} donorUsername=\"{}\" requestId={} matchId={} contactShared=true seekerId={}",
                donorUserId, donor.getUsername(), requestId, savedMatch.getId(), request.getSeeker().getId());
        return savedMatch;
    }

    /**
     * Declines a pending match with donor ownership verification.
     *
     * @param requestId ID of the seeker request
     * @param donorUserId ID of the authenticated donor user
     * @return updated Match entity with status DECLINED
     */
    @Transactional
    public Match declineMatch(Long requestId, Long donorUserId) {
        Match match = matchRepository.findByRequestIdAndDonorId(requestId, donorUserId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No match found for request ID " + requestId + " and donor ID " + donorUserId));

        // Ownership check
        if (!match.getDonor().getId().equals(donorUserId)) {
            throw new UnauthorizedActionException("You cannot decline a match assigned to another donor.");
        }

        match.setStatus(MatchStatus.DECLINED);
        match.setContactShared(false);

        Match saved = matchRepository.save(match);
        log.info("[SECURITY_AUDIT] event=MATCH_DECLINED donorId={} requestId={} matchId={}",
                donorUserId, requestId, saved.getId());
        return saved;
    }
}
