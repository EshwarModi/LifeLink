package com.lifelink.controller;

import com.lifelink.dto.SeekerRequestDto;
import com.lifelink.model.Match;
import com.lifelink.model.SeekerRequest;
import com.lifelink.model.enums.BloodGroup;
import com.lifelink.model.enums.MatchStatus;
import com.lifelink.model.enums.Urgency;
import com.lifelink.repository.MatchRepository;
import com.lifelink.security.CustomUserDetails;
import com.lifelink.service.SeekerRequestService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Optional;

/**
 * Controller for creating, editing, and managing Seeker blood requests.
 * Thin controller delegating all validation and domain mutations to SeekerRequestService.
 */
@Slf4j
@Controller
@RequestMapping("/requests")
@RequiredArgsConstructor
public class RequestController {

    private final SeekerRequestService seekerRequestService;
    private final MatchRepository matchRepository;

    @GetMapping
    public String listOpenRequests(
            @RequestParam(value = "bloodGroup", required = false) BloodGroup bloodGroup,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size,
            Model model) {

        int pageSize = Math.min(Math.max(size, 1), 100);
        Page<SeekerRequest> requestsPage = seekerRequestService.getOpenRequests(
                bloodGroup,
                PageRequest.of(page, pageSize, Sort.by("createdAt").descending())
        );

        model.addAttribute("requestsPage", requestsPage);
        model.addAttribute("selectedBloodGroup", bloodGroup);
        model.addAttribute("bloodGroups", BloodGroup.values());
        return "request/list";
    }

    @GetMapping("/new")
    @PreAuthorize("hasRole('SEEKER')")
    public String showCreateForm(Model model) {
        if (!model.containsAttribute("requestDto")) {
            model.addAttribute("requestDto", new SeekerRequestDto());
        }
        populateReferenceData(model);
        return "request/create";
    }

    @PostMapping
    @PreAuthorize("hasRole('SEEKER')")
    public String processCreate(
            @Valid @ModelAttribute("requestDto") SeekerRequestDto dto,
            BindingResult bindingResult,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            Model model,
            RedirectAttributes redirectAttributes) {

        if (bindingResult.hasErrors()) {
            populateReferenceData(model);
            return "request/create";
        }

        SeekerRequest created = seekerRequestService.createRequest(userDetails.getId(), dto);
        redirectAttributes.addFlashAttribute("successMessage",
                "Blood request #" + created.getId() + " posted successfully! Eligible donors have been notified.");
        return "redirect:/seeker/dashboard";
    }

    @GetMapping("/{id}/edit")
    @PreAuthorize("hasRole('SEEKER')")
    public String showEditForm(
            @PathVariable("id") Long id,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            Model model) {

        SeekerRequest request = seekerRequestService.getRequestById(id);

        if (!request.getSeeker().getId().equals(userDetails.getId())) {
            return "redirect:/seeker/dashboard?error=unauthorized";
        }

        SeekerRequestDto dto = SeekerRequestDto.builder()
                .id(request.getId())
                .bloodGroup(request.getBloodGroup())
                .unitsNeeded(request.getUnitsNeeded())
                .urgency(request.getUrgency())
                .reason(request.getReason())
                .hospitalName(request.getHospitalName())
                .hospitalAddress(request.getHospitalAddress())
                .requiredBy(request.getRequiredBy())
                .build();

        model.addAttribute("requestDto", dto);
        model.addAttribute("requestId", id);
        populateReferenceData(model);
        return "request/edit";
    }

    @PostMapping("/{id}/edit")
    @PreAuthorize("hasRole('SEEKER')")
    public String processEdit(
            @PathVariable("id") Long id,
            @Valid @ModelAttribute("requestDto") SeekerRequestDto dto,
            BindingResult bindingResult,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            Model model,
            RedirectAttributes redirectAttributes) {

        if (bindingResult.hasErrors()) {
            model.addAttribute("requestId", id);
            populateReferenceData(model);
            return "request/edit";
        }

        seekerRequestService.editRequest(userDetails.getId(), id, dto);
        redirectAttributes.addFlashAttribute("successMessage", "Request updated successfully.");
        return "redirect:/seeker/dashboard";
    }

    @PostMapping("/{id}/fulfill")
    @PreAuthorize("hasRole('SEEKER')")
    public String markFulfilled(
            @PathVariable("id") Long id,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            RedirectAttributes redirectAttributes) {

        seekerRequestService.markFulfilled(userDetails.getId(), id);
        redirectAttributes.addFlashAttribute("successMessage", "Request marked as FULFILLED.");
        return "redirect:/seeker/dashboard";
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasRole('SEEKER')")
    public String markCancelled(
            @PathVariable("id") Long id,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            RedirectAttributes redirectAttributes) {

        seekerRequestService.markCancelled(userDetails.getId(), id);
        redirectAttributes.addFlashAttribute("successMessage", "Request CANCELLED.");
        return "redirect:/seeker/dashboard";
    }

    @GetMapping("/{id}")
    public String viewRequestDetail(
            @PathVariable("id") Long id,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            Model model) {

        SeekerRequest request = seekerRequestService.getRequestById(id);
        boolean isOwner = (userDetails != null && request.getSeeker().getId().equals(userDetails.getId()));

        boolean contactRevealed = false;
        Optional<Match> matchForCurrentDonor = Optional.empty();

        if (userDetails != null) {
            matchForCurrentDonor = matchRepository.findByRequestIdAndDonorId(id, userDetails.getId());
            if (matchForCurrentDonor.isPresent() && matchForCurrentDonor.get().isContactShared()) {
                contactRevealed = true;
            }
        }

        List<Match> matches = List.of();
        if (isOwner) {
            matches = matchRepository.findByRequestIdOrderByMatchedAtDesc(id);
        }

        model.addAttribute("request", request);
        model.addAttribute("isOwner", isOwner);
        model.addAttribute("contactRevealed", contactRevealed || isOwner);
        model.addAttribute("donorMatch", matchForCurrentDonor.orElse(null));
        model.addAttribute("matches", matches);

        return "request/detail";
    }

    private void populateReferenceData(Model model) {
        model.addAttribute("bloodGroups", BloodGroup.values());
        model.addAttribute("urgencies", Urgency.values());
    }
}
