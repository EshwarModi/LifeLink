package com.lifelink.service;

import com.lifelink.dto.SeekerRequestForm;
import com.lifelink.model.SeekerRequest;
import com.lifelink.model.User;
import com.lifelink.model.enums.RequestStatus;
import com.lifelink.repository.SeekerRequestRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class SeekerRequestService {

    private final SeekerRequestRepository seekerRequestRepository;
    private final MatchingService matchingService;

    public SeekerRequestService(SeekerRequestRepository seekerRequestRepository, MatchingService matchingService) {
        this.seekerRequestRepository = seekerRequestRepository;
        this.matchingService = matchingService;
    }

    @Transactional
    public SeekerRequest createRequest(SeekerRequestForm form, User seeker) {
        SeekerRequest request = SeekerRequest.builder()
                .seeker(seeker)
                .bloodGroup(form.getBloodGroup())
                .units(form.getUnits())
                .urgency(form.getUrgency())
                .hospitalName(form.getHospitalName().trim())
                .hospitalAddress(form.getHospitalAddress().trim())
                .requiredByDate(form.getRequiredByDate())
                .status(RequestStatus.OPEN)
                .build();

        SeekerRequest savedRequest = seekerRequestRepository.save(request);

        // Auto-match against available donors
        matchingService.createMatchesForRequest(savedRequest);

        return savedRequest;
    }

    public List<SeekerRequest> getRequestsForSeeker(User seeker) {
        return seekerRequestRepository.findBySeekerOrderByCreatedAtDesc(seeker);
    }

    public List<SeekerRequest> getOpenRequestsByBloodGroup(String bloodGroup) {
        return seekerRequestRepository.findByBloodGroupAndStatusOrderByCreatedAtDesc(bloodGroup, RequestStatus.OPEN);
    }
}
