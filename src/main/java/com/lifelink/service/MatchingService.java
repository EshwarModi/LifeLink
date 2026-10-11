package com.lifelink.service;

import com.lifelink.config.MatchingMode;
import com.lifelink.config.MatchingProperties;
import com.lifelink.model.DonorProfile;
import com.lifelink.model.Match;
import com.lifelink.model.SeekerRequest;
import com.lifelink.model.User;
import com.lifelink.model.enums.BloodGroup;
import com.lifelink.model.enums.MatchStatus;
import com.lifelink.model.enums.RequestStatus;
import com.lifelink.notification.MatchCreatedEvent;
import com.lifelink.repository.MatchRepository;
import com.lifelink.repository.SeekerRequestRepository;
import com.lifelink.repository.UserRepository;
import com.lifelink.service.exception.InvalidRequestStateException;
import com.lifelink.service.exception.ResourceNotFoundException;
import com.lifelink.service.exception.UnauthorizedActionException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Matching engine and match lifecycle service for LifeLink.
 *
 * <p><h3>Location-Aware Three-Tier Matching Algorithm (Design Decision #8):</h3>
 * When a blood request is posted or matched, the system searches for available, eligible donors
 * having the exact matching blood group (Design Decision #15) or compatible red blood cell group
 * (Phase 12 feature flag {@code lifelink.matching.mode}) in three progressive geographical tiers,
 * governed by {@code lifelink.matching.min-threshold} (default {@value #MIN_MATCH_THRESHOLD})
 * and {@code lifelink.matching.max-matches} (default {@value #MAX_MATCHES_PER_REQUEST}):
 * <ol>
 *   <li><b>Tier 1 (City-Level):</b> Collect ALL eligible donors within the exact same city as the seeker,
 *       up to the safety cap ({@value #MAX_MATCHES_PER_REQUEST}).</li>
 *   <li><b>Tier 2 (State-Level, excluding city):</b> Run ONLY if the total matched count is strictly below
 *       the minimum threshold ({@value #MIN_MATCH_THRESHOLD}); collect all eligible donors in the same state,
 *       up to the safety cap ({@value #MAX_MATCHES_PER_REQUEST}).</li>
 *   <li><b>Tier 3 (Nationwide/Broad, excluding state):</b> Run ONLY if the total matched count remains strictly
 *       below the minimum threshold ({@value #MIN_MATCH_THRESHOLD}); top up only until the total reaches the
 *       minimum threshold (bounded by the safety cap).</li>
 * </ol>
 * In {@code COMPATIBLE} mode, exact blood group matches are collected first across the three tiers;
 * expansion to compatible non-exact blood groups occurs only if the exact-match total is below the
 * minimum threshold, applying the same tier logic and caps.
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MatchingService {

    /**
     * Default minimum threshold of donor candidates before expanding to broader location tiers
     * or compatible non-exact blood groups.
     */
    public static final int MIN_MATCH_THRESHOLD = 3;

    /**
     * Default hard safety cap on the maximum number of donors matched and notified for a single request.
     */
    public static final int MAX_MATCHES_PER_REQUEST = 25;

    private final UserRepository userRepository;
    private final SeekerRequestRepository seekerRequestRepository;
    private final MatchRepository matchRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final DonorService donorService;
    private final MatchingProperties matchingProperties;

    /**
     * Executes the three-tier matching workflow for an open seeker request.
     * Persists new PENDING matches and publishes {@link MatchCreatedEvent} events
     * for asynchronous {@code AFTER_COMMIT} email dispatch.
     *
     * @param request the open SeekerRequest to match
     * @return list of newly created matches
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

        int minThreshold = resolveMinThreshold();
        int maxMatches = resolveMaxMatches();

        List<User> matchedDonors = new ArrayList<>();
        Set<Long> pickedDonorIds = new HashSet<>();

        // Phase 1: Search exact blood group matches in three tiers (City -> State -> Broad)
        collectDonorsForGroups(
                List.of(bloodGroup), city, state, seekerId, cutoffDate,
                matchedDonors, pickedDonorIds, minThreshold, maxMatches
        );
        log.info("Exact match search for request ID {} found {} donors", request.getId(), matchedDonors.size());

        // Phase 2: In COMPATIBLE mode, if under minThreshold (and below maxMatches), expand to compatible non-exact groups
        MatchingMode mode = (matchingProperties != null && matchingProperties.getMode() != null)
                ? matchingProperties.getMode() : MatchingMode.EXACT;

        if (mode == MatchingMode.COMPATIBLE && matchedDonors.size() < minThreshold && matchedDonors.size() < maxMatches) {
            List<BloodGroup> compatibleNonExact = BloodCompatibility.compatibleDonorsFor(bloodGroup)
                    .stream()
                    .filter(bg -> bg != bloodGroup)
                    .toList();

            if (!compatibleNonExact.isEmpty()) {
                collectDonorsForGroups(
                        compatibleNonExact, city, state, seekerId, cutoffDate,
                        matchedDonors, pickedDonorIds, minThreshold, maxMatches
                );
                log.info("Compatible mode expanded matches for request ID {} to {} donors", request.getId(), matchedDonors.size());
            }
        }

        // Persist matches and publish plain-value events for async AFTER_COMMIT email notifications
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

                // Publish immutable event (handled asynchronously after transaction commit)
                eventPublisher.publishEvent(MatchCreatedEvent.from(donor, request));
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

        // Blood group compatibility check based on lifelink.matching.mode
        MatchingMode mode = (matchingProperties != null && matchingProperties.getMode() != null)
                ? matchingProperties.getMode() : MatchingMode.EXACT;

        if (mode == MatchingMode.EXACT) {
            if (profile.getBloodGroup() != request.getBloodGroup()) {
                throw new InvalidRequestStateException("Donor blood group (" + profile.getBloodGroup() +
                        ") does not match request (" + request.getBloodGroup() + ").");
            }
        } else {
            if (!BloodCompatibility.isCompatible(profile.getBloodGroup(), request.getBloodGroup())) {
                throw new InvalidRequestStateException("Donor blood group (" + profile.getBloodGroup() +
                        ") is not medically compatible with request (" + request.getBloodGroup() + ").");
            }
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

            // State machine check before any state mutation
            MatchStatus currentStatus = match.getStatus();
            MatchStatus.validateTransition(currentStatus, MatchStatus.ACCEPTED);
            if (currentStatus.isIdempotentNoOp(MatchStatus.ACCEPTED)) {
                return match;
            }

            match.setStatus(MatchStatus.ACCEPTED);
            match.setContactShared(true);
        } else {
            // Upsert: (no match row) -> ACCEPTED
            MatchStatus.validateTransition(null, MatchStatus.ACCEPTED);
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
     * Declines a pending match with donor ownership and state machine verification.
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

        // State machine check before any state mutation
        MatchStatus currentStatus = match.getStatus();
        MatchStatus.validateTransition(currentStatus, MatchStatus.DECLINED);
        if (currentStatus.isIdempotentNoOp(MatchStatus.DECLINED)) {
            return match;
        }

        match.setStatus(MatchStatus.DECLINED);
        match.setContactShared(false);

        Match saved = matchRepository.save(match);
        log.info("[SECURITY_AUDIT] event=MATCH_DECLINED donorId={} requestId={} matchId={}",
                donorUserId, requestId, saved.getId());
        return saved;
    }

    /**
     * Queries donors for a specific collection of blood groups progressively across:
     * <ul>
     *   <li><b>Tier 1 (City):</b> Collects all eligible donors in the seeker's city, up to {@code maxMatches}.</li>
     *   <li><b>Tier 2 (State, excluding city):</b> Runs only if total &lt; {@code minThreshold}; collects all eligible
     *       state donors up to {@code maxMatches}.</li>
     *   <li><b>Tier 3 (Nationwide, excluding state):</b> Runs only if total &lt; {@code minThreshold}; tops up only
     *       until total reaches {@code minThreshold} (bounded by {@code maxMatches}).</li>
     * </ul>
     */
    private void collectDonorsForGroups(Collection<BloodGroup> groups,
                                       String city,
                                       String state,
                                       Long seekerId,
                                       LocalDate cutoffDate,
                                       List<User> matchedDonors,
                                       Set<Long> pickedDonorIds,
                                       int minThreshold,
                                       int maxMatches) {
        if (matchedDonors.size() >= minThreshold || matchedDonors.size() >= maxMatches || groups.isEmpty()) {
            return;
        }

        // Tier 1: Exact matching city — collect all eligible donors up to maxMatches
        List<User> tier1Donors = userRepository.findEligibleDonorsByBloodGroupsAndCity(
                groups, city, cutoffDate, seekerId
        );
        for (User u : tier1Donors) {
            if (pickedDonorIds.add(u.getId())) {
                matchedDonors.add(u);
                if (matchedDonors.size() >= maxMatches) {
                    return;
                }
            }
        }

        // Tier 2: State level (excluding city) — run only if total < minThreshold; collect all up to maxMatches
        if (matchedDonors.size() < minThreshold && matchedDonors.size() < maxMatches) {
            List<User> tier2Donors = userRepository.findEligibleDonorsByBloodGroupsAndStateExcludingCity(
                    groups, state, city, cutoffDate, seekerId
            );
            for (User u : tier2Donors) {
                if (pickedDonorIds.add(u.getId())) {
                    matchedDonors.add(u);
                    if (matchedDonors.size() >= maxMatches) {
                        return;
                    }
                }
            }
        }

        // Tier 3: Nationwide (excluding state) — run only if total < minThreshold; top up only to minThreshold
        int tier3Limit = Math.min(minThreshold, maxMatches);
        if (matchedDonors.size() < tier3Limit) {
            List<User> tier3Donors = userRepository.findEligibleDonorsByBloodGroupsBroadExcludingState(
                    groups, state, cutoffDate, seekerId
            );
            for (User u : tier3Donors) {
                if (pickedDonorIds.add(u.getId())) {
                    matchedDonors.add(u);
                    if (matchedDonors.size() >= tier3Limit) {
                        return;
                    }
                }
            }
        }
    }

    private int resolveMinThreshold() {
        if (matchingProperties != null && matchingProperties.getMinThreshold() > 0) {
            return matchingProperties.getMinThreshold();
        }
        return MIN_MATCH_THRESHOLD;
    }

    private int resolveMaxMatches() {
        if (matchingProperties != null && matchingProperties.getMaxMatches() > 0) {
            return matchingProperties.getMaxMatches();
        }
        return MAX_MATCHES_PER_REQUEST;
    }
}
