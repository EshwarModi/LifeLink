package com.lifelink.repository;

import com.lifelink.model.Match;
import com.lifelink.model.SeekerRequest;
import com.lifelink.model.User;
import com.lifelink.model.enums.MatchStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for {@link Match} entities.
 *
 * <p>Handles queries for donor match inboxes, seeker match listings, and canonical
 * match acceptance validation.</p>
 */
@Repository
public interface MatchRepository extends JpaRepository<Match, Long> {

    /**
     * Finds a single match by request entity and donor entity.
     * Prevents duplicate match creation.
     */
    Optional<Match> findByRequestAndDonor(SeekerRequest request, User donor);

    /**
     * Canonical lookup by requestId and donorId.
     * Used to verify ownership before accepting or declining a match (Design Decision #5).
     *
     * @param requestId ID of the associated SeekerRequest
     * @param donorId ID of the donor user
     * @return Optional containing the match if found
     */
    Optional<Match> findByRequestIdAndDonorId(Long requestId, Long donorId);

    /**
     * Fast existence check avoiding entity hydration.
     */
    boolean existsByRequestIdAndDonorId(Long requestId, Long donorId);

    /**
     * Paginated list of matches for a donor, ordered newest first.
     * Used on the Donor dashboard.
     *
     * @param donorId ID of the donor
     * @param pageable pagination parameters
     * @return page of matches
     */
    Page<Match> findByDonorIdOrderByMatchedAtDesc(Long donorId, Pageable pageable);

    /**
     * Unpaginated list of matches for a donor.
     */
    List<Match> findByDonorIdOrderByMatchedAtDesc(Long donorId);

    /**
     * Paginated list of matches for a donor filtered by specific status (e.g., PENDING or ACCEPTED).
     */
    Page<Match> findByDonorIdAndStatus(Long donorId, MatchStatus status, Pageable pageable);

    /**
     * Finds all matches for a donor matching a specific status.
     * Used when a donor toggles unavailable (Design Decision #10) to transition
     * all PENDING matches to EXPIRED.
     *
     * @param donorId ID of the donor
     * @param status MatchStatus.PENDING
     * @return list of matches to expire
     */
    List<Match> findByDonorIdAndStatus(Long donorId, MatchStatus status);

    /**
     * Retrieves all matches generated for a seeker request.
     * Used on the Seeker's request detail view to see matching donors who accepted.
     *
     * @param requestId ID of the request
     * @return list of matches for this request
     */
    List<Match> findByRequestIdOrderByMatchedAtDesc(Long requestId);

    /**
     * Finds matches for a request filtered by status (e.g., PENDING matches to expire when a request passes requiredBy).
     */
    List<Match> findByRequestIdAndStatus(Long requestId, MatchStatus status);

    /**
     * Count how many donors have accepted a specific request.
     */
    long countByRequestIdAndStatus(Long requestId, MatchStatus status);

    /**
     * Count matches across the system by status (used for Admin Dashboard metrics).
     */
    long countByStatus(MatchStatus status);
}
