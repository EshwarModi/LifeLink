package com.lifelink.controller;

import com.lifelink.dto.DonorResponseDto;
import com.lifelink.dto.SeekerRequestResponseDto;
import com.lifelink.model.SeekerRequest;
import com.lifelink.model.User;
import com.lifelink.model.enums.BloodGroup;
import com.lifelink.model.enums.RequestStatus;
import com.lifelink.repository.SeekerRequestRepository;
import com.lifelink.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST API providing paginated JSON responses for open requests and donor searches.
 *
 * <p>Enforces Design Decision #13:
 * Max page size capped at 100 to prevent unbounded database reads.</p>
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ApiController {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int DEFAULT_PAGE_SIZE = 10;

    private final SeekerRequestRepository seekerRequestRepository;
    private final UserRepository userRepository;

    /**
     * Paginated JSON endpoint for open blood requests.
     * Excludes private contact information.
     */
    @GetMapping("/requests")
    public Page<SeekerRequestResponseDto> getOpenRequests(
            @RequestParam(value = "bloodGroup", required = false) BloodGroup bloodGroup,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size) {

        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);

        Page<SeekerRequest> requests;
        if (bloodGroup != null) {
            requests = seekerRequestRepository.findByBloodGroupAndStatus(
                    bloodGroup,
                    RequestStatus.OPEN,
                    PageRequest.of(safePage, safeSize, Sort.by("createdAt").descending())
            );
        } else {
            requests = seekerRequestRepository.findByStatus(
                    RequestStatus.OPEN,
                    PageRequest.of(safePage, safeSize, Sort.by("createdAt").descending())
            );
        }

        return requests.map(SeekerRequestResponseDto::fromEntity);
    }

    /**
     * Paginated JSON endpoint for searching available donors.
     * Strictly preserves donor privacy by omitting phone numbers.
     */
    @GetMapping("/donors")
    public Page<DonorResponseDto> searchDonors(
            @RequestParam(value = "bloodGroup", required = false) BloodGroup bloodGroup,
            @RequestParam(value = "city", required = false) String city,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size) {

        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);

        String normalizedCity = (city != null && !city.trim().isBlank()) ? city.trim() : null;

        Page<User> donors = userRepository.searchAvailableDonors(
                bloodGroup,
                normalizedCity,
                PageRequest.of(safePage, safeSize, Sort.by("city").ascending())
        );

        return donors.map(DonorResponseDto::fromEntity);
    }
}
