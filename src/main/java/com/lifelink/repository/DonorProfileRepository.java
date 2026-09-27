package com.lifelink.repository;

import com.lifelink.model.DonorProfile;
import com.lifelink.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DonorProfileRepository extends JpaRepository<DonorProfile, Long> {
    Optional<DonorProfile> findByUser(User user);
    List<DonorProfile> findByBloodGroupAndIsAvailableTrue(String bloodGroup);
}
