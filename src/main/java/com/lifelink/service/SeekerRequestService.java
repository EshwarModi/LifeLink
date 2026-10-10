package com.lifelink.service;

import com.lifelink.dto.SeekerRequestDto;
import com.lifelink.model.SeekerRequest;
import com.lifelink.model.User;
import com.lifelink.model.enums.BloodGroup;
import com.lifelink.model.enums.RequestStatus;
import com.lifelink.repository.SeekerRequestRepository;
import com.lifelink.repository.UserRepository;
import com.lifelink.service.exception.InvalidRequestStateException;
import com.lifelink.service.exception.ResourceNotFoundException;
import com.lifelink.service.exception.UnauthorizedActionException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * Service managing Seeker blood requests.
 *
 * <p>Enforces Design Decision #6:
 * Validation rules are strictly identical across both creation and editing,
 * requiring valid blood group, units between 1 and 20, and a future required-by date.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SeekerRequestService {

    private final SeekerRequestRepository seekerRequestRepository;
    private final UserRepository userRepository;
    private final MatchingService matchingService;

    /**
     * Creates an open blood request and triggers auto-matching for eligible donors.
     *
     * @param seekerUserId ID of the creating seeker
     * @param dto request data
     * @return the saved SeekerRequest entity
     */
    @Transactional
    public SeekerRequest createRequest(Long seekerUserId, SeekerRequestDto dto) {
        validateRequestDto(dto);

        User seeker = userRepository.findById(seekerUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Seeker user not found with ID: " + seekerUserId));

        SeekerRequest request = SeekerRequest.builder()
                .seeker(seeker)
                .bloodGroup(dto.getBloodGroup())
                .unitsNeeded(dto.getUnitsNeeded())
                .urgency(dto.getUrgency())
                .reason(dto.getReason())
                .hospitalName(dto.getHospitalName().trim())
                .hospitalAddress(dto.getHospitalAddress().trim())
                .requiredBy(dto.getRequiredBy())
                .status(RequestStatus.OPEN)
                .build();

        SeekerRequest savedRequest = seekerRequestRepository.save(request);
        log.info("Created SeekerRequest ID {} for seeker {}", savedRequest.getId(), seeker.getUsername());

        // Flow requirement: Seeker posts request -> system auto-matches donors
        matchingService.findAndCreateMatches(savedRequest);

        return savedRequest;
    }

    /**
     * Edits an open blood request with ownership check and identical validation rules.
     *
     * @param seekerUserId ID of the authenticated seeker
     * @param requestId ID of the request to edit
     * @param dto updated request fields
     * @return updated SeekerRequest entity
     */
    @Transactional
    public SeekerRequest editRequest(Long seekerUserId, Long requestId, SeekerRequestDto dto) {
        validateRequestDto(dto);

        SeekerRequest request = getRequestById(requestId);

        // Ownership check
        if (!request.getSeeker().getId().equals(seekerUserId)) {
            throw new UnauthorizedActionException("You can only edit your own requests.");
        }

        // Only OPEN requests may be modified
        if (request.getStatus() != RequestStatus.OPEN) {
            throw new InvalidRequestStateException("Cannot edit request with status: " + request.getStatus());
        }

        boolean bloodGroupChanged = (request.getBloodGroup() != dto.getBloodGroup());

        request.setBloodGroup(dto.getBloodGroup());
        request.setUnitsNeeded(dto.getUnitsNeeded());
        request.setUrgency(dto.getUrgency());
        request.setReason(dto.getReason());
        request.setHospitalName(dto.getHospitalName().trim());
        request.setHospitalAddress(dto.getHospitalAddress().trim());
        request.setRequiredBy(dto.getRequiredBy());

        SeekerRequest updated = seekerRequestRepository.save(request);
        log.info("Updated SeekerRequest ID {}", updated.getId());

        // If blood group changed, re-run matching engine
        if (bloodGroupChanged) {
            matchingService.findAndCreateMatches(updated);
        }

        return updated;
    }

    /**
     * Marks an open request as FULFILLED.
     */
    @Transactional
    public SeekerRequest markFulfilled(Long seekerUserId, Long requestId) {
        SeekerRequest request = getRequestById(requestId);

        if (!request.getSeeker().getId().equals(seekerUserId)) {
            throw new UnauthorizedActionException("You can only update your own requests.");
        }

        if (request.getStatus() != RequestStatus.OPEN) {
            throw new InvalidRequestStateException("Only OPEN requests can be marked FULFILLED. Current: " + request.getStatus());
        }

        request.setStatus(RequestStatus.FULFILLED);
        SeekerRequest updated = seekerRequestRepository.save(request);
        log.info("SeekerRequest ID {} marked FULFILLED", requestId);
        return updated;
    }

    /**
     * Marks an open request as CANCELLED.
     */
    @Transactional
    public SeekerRequest markCancelled(Long seekerUserId, Long requestId) {
        SeekerRequest request = getRequestById(requestId);

        if (!request.getSeeker().getId().equals(seekerUserId)) {
            throw new UnauthorizedActionException("You can only update your own requests.");
        }

        if (request.getStatus() != RequestStatus.OPEN) {
            throw new InvalidRequestStateException("Only OPEN requests can be marked CANCELLED. Current: " + request.getStatus());
        }

        request.setStatus(RequestStatus.CANCELLED);
        SeekerRequest updated = seekerRequestRepository.save(request);
        log.info("SeekerRequest ID {} marked CANCELLED", requestId);
        return updated;
    }

    @Transactional(readOnly = true)
    public SeekerRequest getRequestById(Long requestId) {
        return seekerRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Request not found with ID: " + requestId));
    }

    @Transactional(readOnly = true)
    public List<SeekerRequest> getRequestsBySeeker(Long seekerUserId) {
        return seekerRequestRepository.findBySeekerIdOrderByCreatedAtDesc(seekerUserId);
    }

    @Transactional(readOnly = true)
    public Page<SeekerRequest> getOpenRequests(BloodGroup bloodGroup, Pageable pageable) {
        if (bloodGroup != null) {
            return seekerRequestRepository.findByBloodGroupAndStatus(bloodGroup, RequestStatus.OPEN, pageable);
        }
        return seekerRequestRepository.findByStatus(RequestStatus.OPEN, pageable);
    }

    /**
     * Enforces identical validation rules for create and edit (Design Decision #6).
     */
    private void validateRequestDto(SeekerRequestDto dto) {
        if (dto.getBloodGroup() == null) {
            throw new IllegalArgumentException("Blood group is required.");
        }
        if (dto.getUnitsNeeded() == null || dto.getUnitsNeeded() < 1 || dto.getUnitsNeeded() > 20) {
            throw new IllegalArgumentException("Units needed must be between 1 and 20.");
        }
        if (dto.getRequiredBy() == null || !dto.getRequiredBy().isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("Required-by date must be strictly in the future.");
        }
        if (dto.getHospitalName() == null || dto.getHospitalName().trim().isBlank()) {
            throw new IllegalArgumentException("Hospital name is required.");
        }
        if (dto.getHospitalAddress() == null || dto.getHospitalAddress().trim().isBlank()) {
            throw new IllegalArgumentException("Hospital address is required.");
        }
    }
}
