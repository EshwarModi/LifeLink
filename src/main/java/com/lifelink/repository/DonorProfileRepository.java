package com.lifelink.repository;

import com.lifelink.model.DonorProfile;
import com.lifelink.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Spring Data JPA repository for {@link DonorProfile} entities.
 *
 * <p>Handles 1:1 profile operations linked with {@link User} entities.</p>
 */
@Repository
public interface DonorProfileRepository extends JpaRepository<DonorProfile, Long> {

    /**
     * Derived query to look up a donor profile by its parent User entity.
     */
    Optional<DonorProfile> findByUser(User user);

    /**
     * Derived query to look up a donor profile directly by the user's ID.
     * Prevents unnecessary User entity fetching when only the ID is present in session.
     */
    Optional<DonorProfile> findByUserId(Long userId);

    /**
     * Fast check whether a user already possesses a donor profile.
     */
    boolean existsByUserId(Long userId);
}
