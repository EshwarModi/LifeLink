package com.lifelink.repository;

import com.lifelink.model.Match;
import com.lifelink.model.SeekerRequest;
import com.lifelink.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MatchRepository extends JpaRepository<Match, Long> {
    List<Match> findByDonorOrderByCreatedAtDesc(User donor);
    List<Match> findBySeekerRequest(SeekerRequest seekerRequest);
    List<Match> findBySeekerRequest_SeekerOrderByCreatedAtDesc(User seeker);
    Optional<Match> findBySeekerRequestAndDonor(SeekerRequest seekerRequest, User donor);
    boolean existsBySeekerRequestAndDonor(SeekerRequest seekerRequest, User donor);
}
