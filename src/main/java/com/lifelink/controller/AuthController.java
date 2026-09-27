package com.lifelink.controller;

import com.lifelink.dto.RegistrationForm;
import com.lifelink.model.enums.UserType;
import com.lifelink.service.UserService;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

@Controller
public class AuthController {

    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    @GetMapping("/register")
    public String showRegistrationForm(Model model) {
        model.addAttribute("registrationForm", new RegistrationForm());
        return "register";
    }

    @PostMapping("/register")
    public String registerUser(@Valid @ModelAttribute("registrationForm") RegistrationForm form,
                               BindingResult bindingResult,
                               Model model) {

        // 1. Check email uniqueness
        if (userService.existsByEmail(form.getEmail())) {
            bindingResult.addError(new FieldError("registrationForm", "email", "Email address is already in use."));
        }

        // 2. Validate Donor-specific fields if user role is DONOR
        if (form.getUserType() == UserType.DONOR) {
            if (form.getBloodGroup() == null || form.getBloodGroup().trim().isEmpty()) {
                bindingResult.addError(new FieldError("registrationForm", "bloodGroup", "Blood group is required for donors."));
            }
            if (form.getAge() == null) {
                bindingResult.addError(new FieldError("registrationForm", "age", "Age is required for donors."));
            } else if (form.getAge() < 18 || form.getAge() > 65) {
                bindingResult.addError(new FieldError("registrationForm", "age", "Donor age must be between 18 and 65."));
            }
            if (form.getGender() == null || form.getGender().trim().isEmpty()) {
                bindingResult.addError(new FieldError("registrationForm", "gender", "Gender is required for donors."));
            }
        }

        if (bindingResult.hasErrors()) {
            return "register";
        }

        userService.registerUser(form);
        return "redirect:/login?registered=true";
    }
}
