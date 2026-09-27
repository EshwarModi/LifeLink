package com.lifelink.controller;

import com.lifelink.dto.SeekerRequestForm;
import com.lifelink.model.User;
import com.lifelink.model.enums.UserType;
import com.lifelink.security.CustomUserDetails;
import com.lifelink.service.SeekerRequestService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/requests")
public class RequestController {

    private final SeekerRequestService seekerRequestService;

    public RequestController(SeekerRequestService seekerRequestService) {
        this.seekerRequestService = seekerRequestService;
    }

    @GetMapping("/new")
    public String showRequestForm(@AuthenticationPrincipal CustomUserDetails userDetails, Model model) {
        if (userDetails.getUser().getUserType() != UserType.SEEKER) {
            return "redirect:/dashboard?error=Only+seekers+can+create+blood+requests";
        }
        model.addAttribute("seekerRequestForm", new SeekerRequestForm());
        return "request-form";
    }

    @PostMapping("/new")
    public String createRequest(@AuthenticationPrincipal CustomUserDetails userDetails,
                                @Valid @ModelAttribute("seekerRequestForm") SeekerRequestForm form,
                                BindingResult bindingResult) {

        User currentUser = userDetails.getUser();
        if (currentUser.getUserType() != UserType.SEEKER) {
            return "redirect:/dashboard?error=Unauthorized+action";
        }

        if (bindingResult.hasErrors()) {
            return "request-form";
        }

        seekerRequestService.createRequest(form, currentUser);
        return "redirect:/dashboard?requestCreated=true";
    }
}
