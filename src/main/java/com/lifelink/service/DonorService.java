package com.lifelink.service;

import com.lifelink.config.MatchingMode;
import com.lifelink.config.MatchingProperties;
import com.lifelink.dto.DonorProfileDto;
import com.lifelink.model.DonorProfile;
import com.lifelink.model.Match;
import com.lifelink.model.enums.BloodGroup;
import com.lifelink.model.enums.MatchStatus;
import com.lifelink.repository.DonorProfileRepository;
import com.lifelink.repository.MatchRepository;
import com.lifelink.service.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Service managing donor profiles, availability state, and donation eligibility.
 *
 * <p>Key Design Decisions:
 * <ul>
 *   <li><b>Named Eligibility Interval:</b> {@value #ELIGIBILITY_INTERVAL_DAYS} days (Design Decision #9).</li>
 *   <li><b>Availability Cascading:</b> When a donor toggles available to false, all their PENDING matches are transitioned to EXPIRED (Design Decision #10).</li>
 *   <li><b>Blood Group Update Cascading:</b> When a donor updates their blood group, any existing PENDING matches that are no longer compatible under the active matching mode are transitioned to EXPIRED.</li>
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
    private final MatchingProperties matchingProperties;

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
     *
     * <p>When the donor's blood group changes, all existing {@code PENDING} matches for this donor are
     * evaluated against the new blood group using the active matching mode ({@code EXACT} equality or
     * {@link BloodCompatibility} in {@code COMPATIBLE} mode). Incompatible {@code PENDING} matches are
     * transitioned to {@code EXPIRED}, while compatible ones are kept. {@code ACCEPTED}, {@code DECLINED},
     * and {@code EXPIRED} matches are untouched.</p>
     */
    @Transactional
    public DonorProfile updateProfile(Long donorUserId, DonorProfileDto dto) {
        DonorProfile profile = getProfileByUserId(donorUserId);
        boolean wasAvailable = profile.isAvailableToDonate();
        BloodGroup oldBloodGroup = profile.getBloodGroup();
        BloodGroup newBloodGroup = dto.getBloodGroup();
        boolean bloodGroupChanged = (oldBloodGroup != newBloodGroup);

        profile.setBloodGroup(newBloodGroup);
        profile.setAge(dto.getAge());
        profile.setGender(dto.getGender());
        profile.setAvailableToDonate(dto.isAvailableToDonate());
        profile.setLastDonationDate(dto.getLastDonationDate());
        profile.setMedicalConditions(dto.getMedicalConditions());

        DonorProfile saved = donorProfileRepository.save(profile);

        if (wasAvailable && !dto.isAvailableToDonate()) {
            expirePendingMatchesForDonor(donorUserId);
        } else if (bloodGroupChanged) {
            expireIncompatiblePendingMatchesForDonor(donorUserId, newBloodGroup);
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

    private void expireIncompatiblePendingMatchesForDonor(Long donorUserId, BloodGroup newDonorGroup) {
        List<Match> pendingMatches = matchRepository.findByDonorIdAndStatus(donorUserId, MatchStatus.PENDING);
        if (pendingMatches.isEmpty()) {
            return;
        }

        MatchingMode mode = (matchingProperties != null && matchingProperties.getMode() != null)
                ? matchingProperties.getMode() : MatchingMode.EXACT;

        List<Match> incompatibleMatches = new ArrayList<>();
        for (Match match : pendingMatches) {
            BloodGroup requestedGroup = match.getRequest().getBloodGroup();
            boolean compatible = (mode == MatchingMode.COMPATIBLE)
                    ? BloodCompatibility.isCompatible(newDonorGroup, requestedGroup)
                    : (newDonorGroup == requestedGroup);

            if (!compatible) {
                match.setStatus(MatchStatus.EXPIRED);
                incompatibleMatches.add(match);
            }
        }

        if (!incompatibleMatches.isEmpty()) {
            matchRepository.saveAll(incompatibleMatches);
            log.info("Transitioned {} incompatible PENDING matches to EXPIRED for donor user ID {} after blood group change to {} (mode={})",
                    incompatibleMatches.size(), donorUserId, newDonorGroup, mode);
        }
    }
}
