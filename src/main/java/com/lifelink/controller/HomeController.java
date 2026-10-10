package com.lifelink.controller;

import com.lifelink.model.enums.BloodGroup;
import com.lifelink.model.enums.RequestStatus;
import com.lifelink.repository.SeekerRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Controller for public landing page and home views.
 */
@Controller
@RequiredArgsConstructor
public class HomeController {

    private final SeekerRequestRepository seekerRequestRepository;
    private final com.lifelink.repository.DonorProfileRepository donorProfileRepository;

    @GetMapping("/")
    public String index(Model model) {
        model.addAttribute("bloodGroups", BloodGroup.values());
        model.addAttribute("recentRequests",
                seekerRequestRepository.findByStatus(
                        RequestStatus.OPEN,
                        PageRequest.of(0, 6, Sort.by("createdAt").descending())
                ).getContent());
        model.addAttribute("totalDonors", donorProfileRepository.count());
        model.addAttribute("totalRequests", seekerRequestRepository.count());
        model.addAttribute("fulfilledRequests",
                seekerRequestRepository.countByStatus(RequestStatus.FULFILLED));
        return "index";
    }
}
