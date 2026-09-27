package com.lifelink.controller;

import com.lifelink.model.DonorProfile;
import com.lifelink.model.Match;
import com.lifelink.model.SeekerRequest;
import com.lifelink.model.User;
import com.lifelink.model.enums.UserType;
import com.lifelink.security.CustomUserDetails;
import com.lifelink.service.DonorProfileService;
import com.lifelink.service.MatchingService;
import com.lifelink.service.SeekerRequestService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

import java.util.List;

@Controller
public class DashboardController {

    private final SeekerRequestService seekerRequestService;
    private final MatchingService matchingService;
    private final DonorProfileService donorProfileService;

    public DashboardController(SeekerRequestService seekerRequestService,
                               MatchingService matchingService,
                               DonorProfileService donorProfileService) {
        this.seekerRequestService = seekerRequestService;
        this.matchingService = matchingService;
        this.donorProfileService = donorProfileService;
    }

    @GetMapping("/dashboard")
    public String dashboard(@AuthenticationPrincipal CustomUserDetails userDetails, Model model) {
        User currentUser = userDetails.getUser();
        model.addAttribute("currentUser", currentUser);

        if (currentUser.getUserType() == UserType.SEEKER) {
            List<SeekerRequest> requests = seekerRequestService.getRequestsForSeeker(currentUser);
            List<Match> seekerMatches = matchingService.getMatchesForSeeker(currentUser);

            model.addAttribute("requests", requests);
            model.addAttribute("seekerMatches", seekerMatches);
            return "dashboard-seeker";

        } else if (currentUser.getUserType() == UserType.DONOR) {
            DonorProfile profile = donorProfileService.getProfileByUser(currentUser)
                    .orElseThrow(() -> new IllegalStateException("Donor profile missing"));

            List<Match> donorMatches = matchingService.getMatchesForDonor(currentUser);
            List<SeekerRequest> openRequests = seekerRequestService.getOpenRequestsByBloodGroup(profile.getBloodGroup());

            model.addAttribute("profile", profile);
            model.addAttribute("donorMatches", donorMatches);
            model.addAttribute("openRequests", openRequests);
            return "dashboard-donor";
        }

        return "redirect:/";
    }

    @PostMapping("/donor/toggle-availability")
    public String toggleAvailability(@AuthenticationPrincipal CustomUserDetails userDetails) {
        donorProfileService.toggleAvailability(userDetails.getUser());
        return "redirect:/dashboard?availabilityToggled=true";
    }
}
