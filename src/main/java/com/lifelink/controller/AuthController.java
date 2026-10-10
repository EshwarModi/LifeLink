package com.lifelink.controller;

import com.lifelink.dto.UserRegistrationDto;
import com.lifelink.model.enums.BloodGroup;
import com.lifelink.model.enums.Gender;
import com.lifelink.model.enums.UserType;
import com.lifelink.service.UserService;
import com.lifelink.service.exception.DuplicateResourceException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Controller for authentication views: login and user registration.
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;

    @GetMapping("/login")
    public String showLoginForm(@RequestParam(value = "error", required = false) String error,
                                @RequestParam(value = "logout", required = false) String logout,
                                @RequestParam(value = "registered", required = false) String registered,
                                Model model) {
        if (error != null) {
            model.addAttribute("errorMessage", "Invalid username or password. Please try again.");
        }
        if (logout != null) {
            model.addAttribute("logoutMessage", "You have successfully signed out.");
        }
        if (registered != null) {
            model.addAttribute("successMessage", "Account created successfully! Please log in.");
        }
        return "auth/login";
    }

    @GetMapping("/register")
    public String showRegistrationForm(Model model) {
        if (!model.containsAttribute("userRegistrationDto")) {
            model.addAttribute("userRegistrationDto", new UserRegistrationDto());
        }
        populateRegistrationReferenceData(model);
        return "auth/register";
    }

    @PostMapping("/register")
    public String processRegistration(
            @Valid @ModelAttribute("userRegistrationDto") UserRegistrationDto dto,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes redirectAttributes) {

        // Extra cross-field validation for DONORs
        if (dto.getUserType() == UserType.DONOR) {
            if (dto.getBloodGroup() == null) {
                bindingResult.rejectValue("bloodGroup", "NotNull", "Blood group is required for donors.");
            }
            if (dto.getGender() == null) {
                bindingResult.rejectValue("gender", "NotNull", "Gender is required for donors.");
            }
            if (dto.getAge() == null) {
                bindingResult.rejectValue("age", "NotNull", "Age is required for donors.");
            }
        }

        if (bindingResult.hasErrors()) {
            populateRegistrationReferenceData(model);
            return "auth/register";
        }

        try {
            userService.registerUser(dto);
            redirectAttributes.addFlashAttribute("successMessage", "Registration complete! You may now log in.");
            return "redirect:/login?registered=true";
        } catch (DuplicateResourceException ex) {
            if (ex.getMessage().contains("Username")) {
                bindingResult.rejectValue("username", "Duplicate", ex.getMessage());
            } else if (ex.getMessage().contains("Email")) {
                bindingResult.rejectValue("email", "Duplicate", ex.getMessage());
            } else {
                model.addAttribute("errorMessage", ex.getMessage());
            }
            populateRegistrationReferenceData(model);
            return "auth/register";
        }
    }

    private void populateRegistrationReferenceData(Model model) {
        model.addAttribute("userTypes", new UserType[]{UserType.DONOR, UserType.SEEKER});
        model.addAttribute("bloodGroups", BloodGroup.values());
        model.addAttribute("genders", Gender.values());
    }
}
