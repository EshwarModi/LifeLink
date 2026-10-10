package com.lifelink.scheduler;

import com.lifelink.model.Match;
import com.lifelink.model.SeekerRequest;
import com.lifelink.model.enums.MatchStatus;
import com.lifelink.model.enums.RequestStatus;
import com.lifelink.repository.MatchRepository;
import com.lifelink.repository.SeekerRequestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * Scheduled background job for expiring overdue blood requests.
 *
 * <p>Enforces Design Decision #11:
 * Runs periodically (default every 15 minutes, configurable via
 * {@code lifelink.scheduler.request-expiration.interval-ms}) to detect OPEN
 * requests whose {@code requiredBy} deadline is in the past.
 * Flips their status to EXPIRED and cascades to expire all associated PENDING matches.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RequestExpirationScheduler {

    private final SeekerRequestRepository seekerRequestRepository;
    private final MatchRepository matchRepository;

    /**
     * Scans for OPEN requests with requiredBy before today, flips them to EXPIRED,
     * and transitions any associated PENDING matches to EXPIRED.
     *
     * @return the number of overdue requests expired in this run
     */
    @Scheduled(
        fixedDelayString = "${lifelink.scheduler.request-expiration.interval-ms:900000}",
        initialDelay = 10000
    )
    @Transactional
    public int expireOverdueRequests() {
        LocalDate today = LocalDate.now();
        List<SeekerRequest> overdueRequests = seekerRequestRepository
                .findByStatusAndRequiredByBefore(RequestStatus.OPEN, today);

        if (overdueRequests.isEmpty()) {
            log.debug("RequestExpirationScheduler: No overdue OPEN requests found.");
            return 0;
        }

        log.info("RequestExpirationScheduler: Found {} overdue OPEN requests past requiredBy date {}",
                overdueRequests.size(), today);

        int expiredMatchesCount = 0;
        for (SeekerRequest request : overdueRequests) {
            request.setStatus(RequestStatus.EXPIRED);

            // Expire all PENDING matches linked to this expired request
            List<Match> pendingMatches = matchRepository.findByRequestIdAndStatus(request.getId(), MatchStatus.PENDING);
            for (Match match : pendingMatches) {
                match.setStatus(MatchStatus.EXPIRED);
                expiredMatchesCount++;
            }
            if (!pendingMatches.isEmpty()) {
                matchRepository.saveAll(pendingMatches);
            }
        }

        seekerRequestRepository.saveAll(overdueRequests);
        log.info("RequestExpirationScheduler: Expired {} requests and {} pending matches.",
                overdueRequests.size(), expiredMatchesCount);

        return overdueRequests.size();
    }
}
