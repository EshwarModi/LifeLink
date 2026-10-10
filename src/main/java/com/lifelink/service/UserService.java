package com.lifelink.service;

import com.lifelink.dto.UserRegistrationDto;
import com.lifelink.model.DonorProfile;
import com.lifelink.model.User;
import com.lifelink.model.enums.UserType;
import com.lifelink.repository.DonorProfileRepository;
import com.lifelink.repository.UserRepository;
import com.lifelink.service.exception.DuplicateResourceException;
import com.lifelink.service.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service managing user registration and account retrieval.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final DonorProfileRepository donorProfileRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * Registers a new user account with duplicate username and email validation.
     * If userType is DONOR, automatically seeds the associated 1:1 DonorProfile.
     *
     * @param dto the registration data transfer object
     * @return the persisted User entity
     */
    @Transactional
    public User registerUser(UserRegistrationDto dto) {
        String trimmedUsername = dto.getUsername().trim();
        String trimmedEmail = dto.getEmail().trim().toLowerCase();

        // 1. Uniqueness checks
        if (userRepository.existsByUsername(trimmedUsername)) {
            throw new DuplicateResourceException("Username '" + trimmedUsername + "' is already taken.");
        }
        if (userRepository.existsByEmail(trimmedEmail)) {
            throw new DuplicateResourceException("Email address '" + trimmedEmail + "' is already registered.");
        }

        // 2. Build User entity with BCrypt hashed password
        User user = User.builder()
                .username(trimmedUsername)
                .email(trimmedEmail)
                .passwordHash(passwordEncoder.encode(dto.getPassword()))
                .fullName(dto.getFullName().trim())
                .phone(dto.getPhone().trim())
                .city(dto.getCity().trim())
                .state(dto.getState().trim())
                .userType(dto.getUserType())
                .isAdmin(dto.getUserType() == UserType.ADMIN)
                .build();

        User savedUser = userRepository.save(user);

        // 3. If registering as a DONOR, create their initial 1:1 DonorProfile
        if (dto.getUserType() == UserType.DONOR) {
            DonorProfile profile = DonorProfile.builder()
                    .user(savedUser)
                    .bloodGroup(dto.getBloodGroup())
                    .age(dto.getAge() != null ? dto.getAge() : 25)
                    .gender(dto.getGender())
                    .availableToDonate(true)
                    .lastDonationDate(dto.getLastDonationDate())
                    .medicalConditions(dto.getMedicalConditions())
                    .build();

            donorProfileRepository.save(profile);
            savedUser.setDonorProfile(profile);
        }

        log.info("[SECURITY_AUDIT] event=USER_REGISTERED userId={} username=\"{}\" role=\"{}\"",
                savedUser.getId(), savedUser.getUsername(), savedUser.getUserType());
        return savedUser;
    }

    @Transactional(readOnly = true)
    public User findById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + id));
    }

    @Transactional(readOnly = true)
    public User findByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with username: " + username));
    }
}
