package com.lifelink.controller;

import com.lifelink.model.Match;
import com.lifelink.security.CustomUserDetails;
import com.lifelink.service.MatchingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Controller managing match responses (acceptance and decline).
 *
 * <p>Enforces Design Decision #5:
 * ONE canonical accept-match endpoint with ownership verification.</p>
 */
@Slf4j
@Controller
@RequestMapping("/matches")
@RequiredArgsConstructor
public class MatchController {

    private final MatchingService matchingService;

    @PostMapping("/{requestId}/accept")
    @PreAuthorize("hasRole('DONOR')")
    public String acceptMatch(
            @PathVariable("requestId") Long requestId,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            RedirectAttributes redirectAttributes) {

        Match acceptedMatch = matchingService.acceptMatch(requestId, userDetails.getId());
        redirectAttributes.addFlashAttribute("successMessage",
                "Thank you for helping! Contact info is now revealed between you and the patient.");
        return "redirect:/requests/" + requestId;
    }

    @PostMapping("/{requestId}/decline")
    @PreAuthorize("hasRole('DONOR')")
    public String declineMatch(
            @PathVariable("requestId") Long requestId,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            RedirectAttributes redirectAttributes) {

        matchingService.declineMatch(requestId, userDetails.getId());
        redirectAttributes.addFlashAttribute("infoMessage", "You have declined this match request.");
        return "redirect:/donor/dashboard";
    }
}
