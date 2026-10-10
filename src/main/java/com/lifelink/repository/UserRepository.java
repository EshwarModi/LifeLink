package com.lifelink.repository;

import com.lifelink.model.User;
import com.lifelink.model.enums.BloodGroup;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for {@link User} entities.
 *
 * <p>Handles account lookups, registration uniqueness checks, and the location-aware
 * donor search queries for the 3-tier matching engine.</p>
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * Derived query to look up a user by their unique username.
     * Essential for Spring Security's UserDetailsService authentication flow.
     */
    Optional<User> findByUsername(String username);

    /**
     * Derived query to look up a user by their unique email address.
     */
    Optional<User> findByEmail(String email);

    /**
     * Fast existence checks using database unique constraints during registration.
     */
    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    /**
     * Count users by UserType (used for Admin Dashboard metrics).
     */
    long countByUserType(com.lifelink.model.enums.UserType userType);

    /**
     * Tier 1 Matching Query:
     * Finds eligible donors matching exact blood group and same city.
     *
     * <p>Eligibility conditions enforced at database level:
     * <ul>
     *   <li>{@code availableToDonate = true}</li>
     *   <li>{@code lastDonationDate IS NULL OR lastDonationDate <= :cutoffDate}</li>
     *   <li>Excludes the requesting seeker themselves</li>
     * </ul>
     * Uses case-insensitive comparison on city.</p>
     *
     * @param bloodGroup exact blood group
     * @param city seeker hospital or location city
     * @param cutoffDate threshold date (today - 90 days)
     * @param excludedUserId seeker user ID to prevent self-matching
     * @return list of matching eligible donor users in the same city
     */
    @Query("""
        SELECT u FROM User u
        JOIN u.donorProfile dp
        WHERE dp.bloodGroup IN :bloodGroups
          AND dp.availableToDonate = true
          AND (dp.lastDonationDate IS NULL OR dp.lastDonationDate <= :cutoffDate)
          AND LOWER(TRIM(u.city)) = LOWER(TRIM(:city))
          AND u.id != :excludedUserId
        ORDER BY dp.lastDonationDate ASC NULLS FIRST
    """)
    List<User> findEligibleDonorsByBloodGroupsAndCity(
        @Param("bloodGroups") Collection<BloodGroup> bloodGroups,
        @Param("city") String city,
        @Param("cutoffDate") LocalDate cutoffDate,
        @Param("excludedUserId") Long excludedUserId
    );

    default List<User> findEligibleDonorsByBloodGroupAndCity(
            BloodGroup bloodGroup, String city, LocalDate cutoffDate, Long excludedUserId) {
        return findEligibleDonorsByBloodGroupsAndCity(List.of(bloodGroup), city, cutoffDate, excludedUserId);
    }

    /**
     * Tier 2 Matching Query:
     * Finds eligible donors matching the given blood group(s) in the same state,
     * excluding donors from the same city (who were already evaluated in Tier 1).
     *
     * @param bloodGroups collection of matching or compatible blood groups
     * @param state seeker hospital or location state
     * @param city seeker city (to exclude duplicates already returned)
     * @param cutoffDate threshold date (today - 90 days)
     * @param excludedUserId seeker user ID
     * @return list of matching eligible donor users in the same state
     */
    @Query("""
        SELECT u FROM User u
        JOIN u.donorProfile dp
        WHERE dp.bloodGroup IN :bloodGroups
          AND dp.availableToDonate = true
          AND (dp.lastDonationDate IS NULL OR dp.lastDonationDate <= :cutoffDate)
          AND LOWER(TRIM(u.state)) = LOWER(TRIM(:state))
          AND LOWER(TRIM(u.city)) != LOWER(TRIM(:city))
          AND u.id != :excludedUserId
        ORDER BY dp.lastDonationDate ASC NULLS FIRST
    """)
    List<User> findEligibleDonorsByBloodGroupsAndStateExcludingCity(
        @Param("bloodGroups") Collection<BloodGroup> bloodGroups,
        @Param("state") String state,
        @Param("city") String city,
        @Param("cutoffDate") LocalDate cutoffDate,
        @Param("excludedUserId") Long excludedUserId
    );

    default List<User> findEligibleDonorsByBloodGroupAndStateExcludingCity(
            BloodGroup bloodGroup, String state, String city, LocalDate cutoffDate, Long excludedUserId) {
        return findEligibleDonorsByBloodGroupsAndStateExcludingCity(List.of(bloodGroup), state, city, cutoffDate, excludedUserId);
    }

    /**
     * Tier 3 Matching Query:
     * Finds eligible donors nationwide matching the given blood group(s),
     * excluding donors from the same state (already evaluated in Tier 1 and 2).
     *
     * @param bloodGroups collection of matching or compatible blood groups
     * @param state seeker state (to exclude donors already returned)
     * @param cutoffDate threshold date (today - 90 days)
     * @param excludedUserId seeker user ID
     * @return list of matching eligible donor users across the country
     */
    @Query("""
        SELECT u FROM User u
        JOIN u.donorProfile dp
        WHERE dp.bloodGroup IN :bloodGroups
          AND dp.availableToDonate = true
          AND (dp.lastDonationDate IS NULL OR dp.lastDonationDate <= :cutoffDate)
          AND LOWER(TRIM(u.state)) != LOWER(TRIM(:state))
          AND u.id != :excludedUserId
        ORDER BY dp.lastDonationDate ASC NULLS FIRST
    """)
    List<User> findEligibleDonorsByBloodGroupsBroadExcludingState(
        @Param("bloodGroups") Collection<BloodGroup> bloodGroups,
        @Param("state") String state,
        @Param("cutoffDate") LocalDate cutoffDate,
        @Param("excludedUserId") Long excludedUserId
    );

    default List<User> findEligibleDonorsByBloodGroupBroadExcludingState(
            BloodGroup bloodGroup, String state, LocalDate cutoffDate, Long excludedUserId) {
        return findEligibleDonorsByBloodGroupsBroadExcludingState(List.of(bloodGroup), state, cutoffDate, excludedUserId);
    }

    /**
     * Paginated list of all active donors (for directory listings).
     */
    @Query("""
        SELECT u FROM User u
        JOIN u.donorProfile dp
        WHERE dp.availableToDonate = true
    """)
    Page<User> findAllAvailableDonors(Pageable pageable);

    /**
     * Paginated search for available donors with optional blood group and city filters.
     */
    @Query("""
        SELECT u FROM User u
        JOIN u.donorProfile dp
        WHERE dp.availableToDonate = true
          AND (:bloodGroup IS NULL OR dp.bloodGroup = :bloodGroup)
          AND (:city IS NULL OR LOWER(TRIM(u.city)) = LOWER(TRIM(:city)))
    """)
    Page<User> searchAvailableDonors(
        @Param("bloodGroup") BloodGroup bloodGroup,
        @Param("city") String city,
        Pageable pageable
    );
}
