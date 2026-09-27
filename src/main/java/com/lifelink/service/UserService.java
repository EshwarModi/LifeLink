package com.lifelink.service;

import com.lifelink.dto.RegistrationForm;
import com.lifelink.model.DonorProfile;
import com.lifelink.model.User;
import com.lifelink.model.enums.UserType;
import com.lifelink.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public User registerUser(RegistrationForm form) {
        if (userRepository.existsByEmail(form.getEmail())) {
            throw new IllegalArgumentException("Email address is already registered.");
        }

        User user = User.builder()
                .email(form.getEmail().trim().toLowerCase())
                .password(passwordEncoder.encode(form.getPassword()))
                .fullName(form.getFullName().trim())
                .phone(form.getPhone().trim())
                .userType(form.getUserType())
                .build();

        if (form.getUserType() == UserType.DONOR) {
            DonorProfile profile = DonorProfile.builder()
                    .user(user)
                    .bloodGroup(form.getBloodGroup())
                    .age(form.getAge())
                    .gender(form.getGender())
                    .isAvailable(form.getIsAvailable() != null ? form.getIsAvailable() : true)
                    .build();
            user.setDonorProfile(profile);
        }

        return userRepository.save(user);
    }

    public Optional<User> findByEmail(String email) {
        return userRepository.findByEmail(email.trim().toLowerCase());
    }

    public boolean existsByEmail(String email) {
        return userRepository.existsByEmail(email.trim().toLowerCase());
    }
}
