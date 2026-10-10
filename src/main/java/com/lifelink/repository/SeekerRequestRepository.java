package com.lifelink.repository;

import com.lifelink.model.SeekerRequest;
import com.lifelink.model.enums.BloodGroup;
import com.lifelink.model.enums.RequestStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

/**
 * Spring Data JPA repository for {@link SeekerRequest} entities.
 *
 * <p>Supports paginated searches of open requests, seeker history, and background expiration jobs.</p>
 */
@Repository
public interface SeekerRequestRepository extends JpaRepository<SeekerRequest, Long> {

    /**
     * Derived query for paginated listing of open requests filtered by blood group.
     * Utilizes composite index {@code idx_requests_bg_status (blood_group, status)}.
     *
     * @param bloodGroup the requested blood group
     * @param status typically RequestStatus.OPEN
     * @param pageable pagination and sorting parameters
     * @return page of matching requests
     */
    Page<SeekerRequest> findByBloodGroupAndStatus(BloodGroup bloodGroup, RequestStatus status, Pageable pageable);

    /**
     * Derived query for paginated listing of requests by status across all blood groups.
     * Utilizes index {@code idx_requests_status}.
     *
     * @param status request lifecycle status (OPEN, FULFILLED, etc.)
     * @param pageable pagination parameters
     * @return page of requests matching status
     */
    Page<SeekerRequest> findByStatus(RequestStatus status, Pageable pageable);

    /**
     * Derived query to list all requests created by a specific seeker, ordered newest first.
     * Used on the Seeker dashboard.
     *
     * @param seekerId ID of the seeker user
     * @return list of requests created by the seeker
     */
    List<SeekerRequest> findBySeekerIdOrderByCreatedAtDesc(Long seekerId);

    /**
     * Paginated version of seeker's request history.
     */
    Page<SeekerRequest> findBySeekerId(Long seekerId, Pageable pageable);

    /**
     * Used by the background scheduler (Design Decision #11):
     * Finds all OPEN requests whose `requiredBy` deadline has strictly passed.
     *
     * @param status typically RequestStatus.OPEN
     * @param cutoffDate typically LocalDate.now()
     * @return list of overdue open requests to be marked EXPIRED
     */
    List<SeekerRequest> findByStatusAndRequiredByBefore(RequestStatus status, LocalDate cutoffDate);

    /**
     * Count requests by status (e.g., FULFILLED count for public stats).
     */
    long countByStatus(RequestStatus status);

    /**
     * Optional search method allowing combined optional filters in a single JPQL query.
     */
    @Query("""
        SELECT r FROM SeekerRequest r
        WHERE (:bloodGroup IS NULL OR r.bloodGroup = :bloodGroup)
          AND (:status IS NULL OR r.status = :status)
    """)
    Page<SeekerRequest> searchRequests(
        @Param("bloodGroup") BloodGroup bloodGroup,
        @Param("status") RequestStatus status,
        Pageable pageable
    );
}
