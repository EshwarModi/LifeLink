package com.lifelink.service;

import com.lifelink.dto.DonorProfileDto;
import com.lifelink.model.DonorProfile;
import com.lifelink.model.Match;
import com.lifelink.model.enums.MatchStatus;
import com.lifelink.repository.DonorProfileRepository;
import com.lifelink.repository.MatchRepository;
import com.lifelink.service.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * Service managing donor profiles, availability state, and donation eligibility.
 *
 * <p>Key Design Decisions:
 * <ul>
 *   <li><b>Named Eligibility Interval:</b> {@value #ELIGIBILITY_INTERVAL_DAYS} days (Design Decision #9).</li>
 *   <li><b>Availability Cascading:</b> When a donor toggles available to false, all their PENDING matches are transitioned to EXPIRED (Design Decision #10).</li>
 * </ul>
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DonorService {

    /**
     * Minimum interval in days between whole blood donations.
     */
    public static final int ELIGIBILITY_INTERVAL_DAYS = 90;

    private final DonorProfileRepository donorProfileRepository;
    private final MatchRepository matchRepository;

    /**
     * Toggles a donor's availability.
     * When toggled to unavailable (false), any existing PENDING matches for this donor
     * are automatically expired to prevent stale requests from lingering.
     *
     * @param donorUserId user ID of the donor
     * @param available new availability flag
     * @return updated DonorProfile
     */
    @Transactional
    public DonorProfile toggleAvailability(Long donorUserId, boolean available) {
        DonorProfile profile = getProfileByUserId(donorUserId);
        boolean previouslyAvailable = profile.isAvailableToDonate();
        profile.setAvailableToDonate(available);

        DonorProfile updatedProfile = donorProfileRepository.save(profile);

        // Design Decision #10: Cascading PENDING matches to EXPIRED when donor goes unavailable
        if (previouslyAvailable && !available) {
            expirePendingMatchesForDonor(donorUserId);
        }

        log.info("Donor (User ID: {}) availability changed to {}", donorUserId, available);
        return updatedProfile;
    }

    /**
     * Updates donor profile fields (blood group, age, gender, medical conditions, last donation date).
     */
    @Transactional
    public DonorProfile updateProfile(Long donorUserId, DonorProfileDto dto) {
        DonorProfile profile = getProfileByUserId(donorUserId);
        boolean wasAvailable = profile.isAvailableToDonate();

        profile.setBloodGroup(dto.getBloodGroup());
        profile.setAge(dto.getAge());
        profile.setGender(dto.getGender());
        profile.setAvailableToDonate(dto.isAvailableToDonate());
        profile.setLastDonationDate(dto.getLastDonationDate());
        profile.setMedicalConditions(dto.getMedicalConditions());

        DonorProfile saved = donorProfileRepository.save(profile);

        if (wasAvailable && !dto.isAvailableToDonate()) {
            expirePendingMatchesForDonor(donorUserId);
        }

        return saved;
    }

    /**
     * Evaluates whether a donor is medically and operationally eligible to donate today.
     * Enforces: availableToDonate == true AND (no prior donation OR last donation >= 90 days ago).
     *
     * @param profile donor profile
     * @return true if currently eligible to be matched
     */
    public boolean isDonorEligible(DonorProfile profile) {
        if (profile == null || !profile.isAvailableToDonate()) {
            return false;
        }

        LocalDate lastDonation = profile.getLastDonationDate();
        if (lastDonation == null) {
            return true;
        }

        LocalDate eligibilityThreshold = LocalDate.now().minusDays(ELIGIBILITY_INTERVAL_DAYS);
        return !lastDonation.isAfter(eligibilityThreshold);
    }

    @Transactional(readOnly = true)
    public DonorProfile getProfileByUserId(Long userId) {
        return donorProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Donor profile not found for user ID: " + userId));
    }

    private void expirePendingMatchesForDonor(Long donorUserId) {
        List<Match> pendingMatches = matchRepository.findByDonorIdAndStatus(donorUserId, MatchStatus.PENDING);
        if (!pendingMatches.isEmpty()) {
            for (Match match : pendingMatches) {
                match.setStatus(MatchStatus.EXPIRED);
            }
            matchRepository.saveAll(pendingMatches);
            log.info("Transitioned {} PENDING matches to EXPIRED for donor user ID {}", pendingMatches.size(), donorUserId);
        }
    }
}
