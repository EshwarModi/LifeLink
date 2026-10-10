package com.lifelink.controller;

import com.lifelink.dto.DonorProfileDto;
import com.lifelink.model.DonorProfile;
import com.lifelink.model.enums.BloodGroup;
import com.lifelink.model.enums.Gender;
import com.lifelink.security.CustomUserDetails;
import com.lifelink.service.DonorService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Controller managing Donor-specific actions: availability toggle and profile editing.
 */
@Slf4j
@Controller
@RequestMapping("/donor")
@PreAuthorize("hasRole('DONOR')")
@RequiredArgsConstructor
public class DonorController {

    private final DonorService donorService;

    @PostMapping("/availability")
    public String toggleAvailability(
            @RequestParam("available") boolean available,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            RedirectAttributes redirectAttributes) {

        donorService.toggleAvailability(userDetails.getId(), available);
        String msg = available
                ? "Your status is now AVAILABLE to donate blood."
                : "Your status is now UNAVAILABLE. Any pending match requests have been expired.";
        redirectAttributes.addFlashAttribute("successMessage", msg);
        return "redirect:/donor/dashboard";
    }

    @GetMapping("/profile")
    public String viewProfile(@AuthenticationPrincipal CustomUserDetails userDetails, Model model) {
        DonorProfile profile = donorService.getProfileByUserId(userDetails.getId());

        DonorProfileDto dto = DonorProfileDto.builder()
                .bloodGroup(profile.getBloodGroup())
                .age(profile.getAge())
                .gender(profile.getGender())
                .availableToDonate(profile.isAvailableToDonate())
                .lastDonationDate(profile.getLastDonationDate())
                .medicalConditions(profile.getMedicalConditions())
                .build();

        model.addAttribute("profileDto", dto);
        populateDonorReferenceData(model);
        return "donor/profile";
    }

    @PostMapping("/profile")
    public String updateProfile(
            @Valid @ModelAttribute("profileDto") DonorProfileDto dto,
            BindingResult bindingResult,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            Model model,
            RedirectAttributes redirectAttributes) {

        if (bindingResult.hasErrors()) {
            populateDonorReferenceData(model);
            return "donor/profile";
        }

        donorService.updateProfile(userDetails.getId(), dto);
        redirectAttributes.addFlashAttribute("successMessage", "Profile updated successfully.");
        return "redirect:/donor/dashboard";
    }

    private void populateDonorReferenceData(Model model) {
        model.addAttribute("bloodGroups", BloodGroup.values());
        model.addAttribute("genders", Gender.values());
    }
}
