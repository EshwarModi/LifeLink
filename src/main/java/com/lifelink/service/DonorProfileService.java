package com.lifelink.service;

import com.lifelink.model.DonorProfile;
import com.lifelink.model.User;
import com.lifelink.repository.DonorProfileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class DonorProfileService {

    private final DonorProfileRepository donorProfileRepository;

    public DonorProfileService(DonorProfileRepository donorProfileRepository) {
        this.donorProfileRepository = donorProfileRepository;
    }

    public Optional<DonorProfile> getProfileByUser(User user) {
        return donorProfileRepository.findByUser(user);
    }

    @Transactional
    public DonorProfile toggleAvailability(User user) {
        DonorProfile profile = donorProfileRepository.findByUser(user)
                .orElseThrow(() -> new IllegalArgumentException("Donor profile not found"));
        profile.setIsAvailable(!profile.getIsAvailable());
        return donorProfileRepository.save(profile);
    }
}
