package com.lifelink.service;

import com.lifelink.model.DonorProfile;
import com.lifelink.model.Match;
import com.lifelink.model.SeekerRequest;
import com.lifelink.model.User;
import com.lifelink.model.enums.MatchStatus;
import com.lifelink.model.enums.UserType;
import com.lifelink.repository.DonorProfileRepository;
import com.lifelink.repository.MatchRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class MatchingService {

    private final DonorProfileRepository donorProfileRepository;
    private final MatchRepository matchRepository;

    public MatchingService(DonorProfileRepository donorProfileRepository, MatchRepository matchRepository) {
        this.donorProfileRepository = donorProfileRepository;
        this.matchRepository = matchRepository;
    }

    @Transactional
    public List<Match> createMatchesForRequest(SeekerRequest seekerRequest) {
        // Find all available donors with the EXACT blood group
        List<DonorProfile> availableDonors = donorProfileRepository
                .findByBloodGroupAndIsAvailableTrue(seekerRequest.getBloodGroup());

        List<Match> createdMatches = new ArrayList<>();

        for (DonorProfile donorProfile : availableDonors) {
            User donorUser = donorProfile.getUser();

            // Ensure donor is a DONOR user and not the seeker themselves
            if (donorUser.getUserType() == UserType.DONOR &&
                !donorUser.getId().equals(seekerRequest.getSeeker().getId())) {

                if (!matchRepository.existsBySeekerRequestAndDonor(seekerRequest, donorUser)) {
                    Match match = Match.builder()
                            .seekerRequest(seekerRequest)
                            .donor(donorUser)
                            .status(MatchStatus.PENDING)
                            .contactShared(false)
                            .build();

                    createdMatches.add(matchRepository.save(match));
                }
            }
        }

        return createdMatches;
    }

    @Transactional
    public Match acceptMatch(Long matchId, User donorUser) {
        Match match = matchRepository.findById(matchId)
                .orElseThrow(() -> new IllegalArgumentException("Match not found with ID: " + matchId));

        // Ownership Check: Ensure logged in user is the assigned donor for this match
        if (!match.getDonor().getId().equals(donorUser.getId())) {
            throw new SecurityException("Unauthorized: You cannot accept a match assigned to another donor.");
        }

        match.setStatus(MatchStatus.ACCEPTED);
        match.setContactShared(true);
        return matchRepository.save(match);
    }

    @Transactional
    public Match declineMatch(Long matchId, User donorUser) {
        Match match = matchRepository.findById(matchId)
                .orElseThrow(() -> new IllegalArgumentException("Match not found with ID: " + matchId));

        // Ownership Check: Ensure logged in user is the assigned donor for this match
        if (!match.getDonor().getId().equals(donorUser.getId())) {
            throw new SecurityException("Unauthorized: You cannot decline a match assigned to another donor.");
        }

        match.setStatus(MatchStatus.DECLINED);
        return matchRepository.save(match);
    }

    public List<Match> getMatchesForDonor(User donorUser) {
        return matchRepository.findByDonorOrderByCreatedAtDesc(donorUser);
    }

    public List<Match> getMatchesForSeeker(User seekerUser) {
        return matchRepository.findBySeekerRequest_SeekerOrderByCreatedAtDesc(seekerUser);
    }
}
