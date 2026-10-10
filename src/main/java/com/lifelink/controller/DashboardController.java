package com.lifelink.controller;

import com.lifelink.model.DonorProfile;
import com.lifelink.model.Match;
import com.lifelink.model.SeekerRequest;
import com.lifelink.model.enums.RequestStatus;
import com.lifelink.model.enums.UserType;
import com.lifelink.repository.MatchRepository;
import com.lifelink.repository.SeekerRequestRepository;
import com.lifelink.security.CustomUserDetails;
import com.lifelink.service.DonorService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

/**
 * Controller providing role-segregated dashboards for Donors, Seekers, and Admins.
 */
@Controller
@RequiredArgsConstructor
public class DashboardController {

    private final DonorService donorService;
    private final MatchRepository matchRepository;
    private final SeekerRequestRepository seekerRequestRepository;
    private final com.lifelink.repository.UserRepository userRepository;

    @GetMapping("/dashboard")
    public String routeToRoleDashboard(@AuthenticationPrincipal CustomUserDetails userDetails) {
        if (userDetails.isAdmin()) {
            return "redirect:/admin/dashboard";
        }
        if (userDetails.getUserType() == UserType.DONOR) {
            return "redirect:/donor/dashboard";
        }
        return "redirect:/seeker/dashboard";
    }

    @GetMapping("/donor/dashboard")
    @PreAuthorize("hasRole('DONOR')")
    public String donorDashboard(@AuthenticationPrincipal CustomUserDetails userDetails, Model model) {
        DonorProfile profile = donorService.getProfileByUserId(userDetails.getId());
        boolean isEligible = donorService.isDonorEligible(profile);

        java.time.LocalDate nextEligibleDate = null;
        if (profile.getLastDonationDate() != null) {
            nextEligibleDate = profile.getLastDonationDate().plusDays(com.lifelink.service.DonorService.ELIGIBILITY_INTERVAL_DAYS);
        }

        List<Match> matches = matchRepository.findByDonorIdOrderByMatchedAtDesc(userDetails.getId());
        List<SeekerRequest> openMatchingRequests = seekerRequestRepository
                .findByBloodGroupAndStatus(
                        profile.getBloodGroup(),
                        RequestStatus.OPEN,
                        PageRequest.of(0, 5, Sort.by("createdAt").descending())
                ).getContent();

        model.addAttribute("user", userDetails);
        model.addAttribute("profile", profile);
        model.addAttribute("isEligible", isEligible);
        model.addAttribute("nextEligibleDate", nextEligibleDate);
        model.addAttribute("matches", matches);
        model.addAttribute("openRequests", openMatchingRequests);

        return "donor/dashboard";
    }

    @GetMapping("/seeker/dashboard")
    @PreAuthorize("hasRole('SEEKER')")
    public String seekerDashboard(@AuthenticationPrincipal CustomUserDetails userDetails, Model model) {
        List<SeekerRequest> allRequests = seekerRequestRepository
                .findBySeekerIdOrderByCreatedAtDesc(userDetails.getId());

        List<SeekerRequest> activeRequests = allRequests.stream()
                .filter(r -> r.getStatus() == RequestStatus.OPEN)
                .toList();

        List<SeekerRequest> historicalRequests = allRequests.stream()
                .filter(r -> r.getStatus() != RequestStatus.OPEN)
                .toList();

        model.addAttribute("user", userDetails);
        model.addAttribute("requests", allRequests);
        model.addAttribute("activeRequests", activeRequests);
        model.addAttribute("historicalRequests", historicalRequests);

        return "seeker/dashboard";
    }

    @GetMapping("/admin/dashboard")
    @PreAuthorize("hasRole('ADMIN')")
    public String adminDashboard(@AuthenticationPrincipal CustomUserDetails userDetails, Model model) {
        // User counts by type
        long donorCount = userRepository.countByUserType(UserType.DONOR);
        long seekerCount = userRepository.countByUserType(UserType.SEEKER);
        long adminCount = userRepository.countByUserType(UserType.ADMIN);
        long totalUsers = userRepository.count();

        // Request counts by status
        long openRequests = seekerRequestRepository.countByStatus(RequestStatus.OPEN);
        long fulfilledRequests = seekerRequestRepository.countByStatus(RequestStatus.FULFILLED);
        long cancelledRequests = seekerRequestRepository.countByStatus(RequestStatus.CANCELLED);
        long expiredRequests = seekerRequestRepository.countByStatus(RequestStatus.EXPIRED);
        long totalRequests = seekerRequestRepository.count();

        // Match counts by status
        long pendingMatches = matchRepository.countByStatus(com.lifelink.model.enums.MatchStatus.PENDING);
        long acceptedMatches = matchRepository.countByStatus(com.lifelink.model.enums.MatchStatus.ACCEPTED);
        long declinedMatches = matchRepository.countByStatus(com.lifelink.model.enums.MatchStatus.DECLINED);
        long expiredMatches = matchRepository.countByStatus(com.lifelink.model.enums.MatchStatus.EXPIRED);
        long totalMatches = matchRepository.count();

        // 10 most recent requests
        List<SeekerRequest> recentRequests = seekerRequestRepository.findAll(
                PageRequest.of(0, 10, Sort.by("createdAt").descending())
        ).getContent();

        model.addAttribute("user", userDetails);
        model.addAttribute("totalUsers", totalUsers);
        model.addAttribute("donorCount", donorCount);
        model.addAttribute("seekerCount", seekerCount);
        model.addAttribute("adminCount", adminCount);

        model.addAttribute("totalRequests", totalRequests);
        model.addAttribute("openRequests", openRequests);
        model.addAttribute("fulfilledRequests", fulfilledRequests);
        model.addAttribute("cancelledRequests", cancelledRequests);
        model.addAttribute("expiredRequests", expiredRequests);

        model.addAttribute("totalMatches", totalMatches);
        model.addAttribute("pendingMatches", pendingMatches);
        model.addAttribute("acceptedMatches", acceptedMatches);
        model.addAttribute("declinedMatches", declinedMatches);
        model.addAttribute("expiredMatches", expiredMatches);

        model.addAttribute("recentRequests", recentRequests);

        return "admin/dashboard";
    }
}
