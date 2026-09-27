package com.lifelink.repository;

import com.lifelink.model.SeekerRequest;
import com.lifelink.model.User;
import com.lifelink.model.enums.RequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SeekerRequestRepository extends JpaRepository<SeekerRequest, Long> {
    List<SeekerRequest> findBySeekerOrderByCreatedAtDesc(User seeker);
    List<SeekerRequest> findByBloodGroupAndStatusOrderByCreatedAtDesc(String bloodGroup, RequestStatus status);
}
