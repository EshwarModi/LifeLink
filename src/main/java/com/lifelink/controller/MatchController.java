package com.lifelink.controller;

import com.lifelink.model.User;
import com.lifelink.security.CustomUserDetails;
import com.lifelink.service.MatchingService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/matches")
public class MatchController {

    private final MatchingService matchingService;

    public MatchController(MatchingService matchingService) {
        this.matchingService = matchingService;
    }

    @PostMapping("/{id}/accept")
    public String acceptMatch(@PathVariable("id") Long matchId,
                              @AuthenticationPrincipal CustomUserDetails userDetails) {
        User currentUser = userDetails.getUser();
        matchingService.acceptMatch(matchId, currentUser);
        return "redirect:/dashboard?matchAccepted=true";
    }

    @PostMapping("/{id}/decline")
    public String declineMatch(@PathVariable("id") Long matchId,
                               @AuthenticationPrincipal CustomUserDetails userDetails) {
        User currentUser = userDetails.getUser();
        matchingService.declineMatch(matchId, currentUser);
        return "redirect:/dashboard?matchDeclined=true";
    }
}
