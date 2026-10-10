package com.lifelink.service;

import com.lifelink.model.DonorProfile;
import com.lifelink.model.Match;
import com.lifelink.model.enums.BloodGroup;
import com.lifelink.model.enums.Gender;
import com.lifelink.model.enums.MatchStatus;
import com.lifelink.repository.DonorProfileRepository;
import com.lifelink.repository.MatchRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pure JUnit 5 + Mockito unit tests for {@link DonorService}.
 *
 * Verifies:
 * 1. 90-day eligibility business rules (Design Decision #9).
 * 2. Cascading transition of PENDING matches to EXPIRED upon availability toggle (Design Decision #10).
 */
@ExtendWith(MockitoExtension.class)
class DonorServiceUnitTest {

    @Mock
    private DonorProfileRepository donorProfileRepository;

    @Mock
    private MatchRepository matchRepository;

    @InjectMocks
    private DonorService donorService;

    // =========================================================================
    // Donor Eligibility Tests (Design Decision #9)
    // =========================================================================

    @Test
    @DisplayName("Eligibility: null profile returns false")
    void testIsDonorEligible_NullProfile_ReturnsFalse() {
        assertThat(donorService.isDonorEligible(null)).isFalse();
    }

    @Test
    @DisplayName("Eligibility: availableToDonate == false returns false even with no prior donation")
    void testIsDonorEligible_UnavailableNoPriorDonation_ReturnsFalse() {
        DonorProfile profile = DonorProfile.builder()
                .availableToDonate(false)
                .lastDonationDate(null)
                .build();

        assertThat(donorService.isDonorEligible(profile)).isFalse();
    }

    @Test
    @DisplayName("Eligibility: availableToDonate == true with no prior donation (first time) returns true")
    void testIsDonorEligible_AvailableFirstTimeDonor_ReturnsTrue() {
        DonorProfile profile = DonorProfile.builder()
                .availableToDonate(true)
                .lastDonationDate(null)
                .build();

        assertThat(donorService.isDonorEligible(profile)).isTrue();
    }

    @Test
    @DisplayName("Eligibility: availableToDonate == true with donation exactly 90 days ago returns true")
    void testIsDonorEligible_AvailableExact90Days_ReturnsTrue() {
        DonorProfile profile = DonorProfile.builder()
                .availableToDonate(true)
                .lastDonationDate(LocalDate.now().minusDays(DonorService.ELIGIBILITY_INTERVAL_DAYS))
                .build();

        assertThat(donorService.isDonorEligible(profile)).isTrue();
    }

    @Test
    @DisplayName("Eligibility: availableToDonate == true with donation 120 days ago (> 90) returns true")
    void testIsDonorEligible_AvailableOver90Days_ReturnsTrue() {
        DonorProfile profile = DonorProfile.builder()
                .availableToDonate(true)
                .lastDonationDate(LocalDate.now().minusDays(120))
                .build();

        assertThat(donorService.isDonorEligible(profile)).isTrue();
    }

    @Test
    @DisplayName("Eligibility: availableToDonate == true with donation 89 days ago (< 90) returns false")
    void testIsDonorEligible_Available89DaysAgo_ReturnsFalse() {
        DonorProfile profile = DonorProfile.builder()
                .availableToDonate(true)
                .lastDonationDate(LocalDate.now().minusDays(89))
                .build();

        assertThat(donorService.isDonorEligible(profile)).isFalse();
    }

    @Test
    @DisplayName("Eligibility: availableToDonate == true with recent donation (yesterday) returns false")
    void testIsDonorEligible_AvailableRecentDonation_ReturnsFalse() {
        DonorProfile profile = DonorProfile.builder()
                .availableToDonate(true)
                .lastDonationDate(LocalDate.now().minusDays(1))
                .build();

        assertThat(donorService.isDonorEligible(profile)).isFalse();
    }

    // =========================================================================
    // Availability Toggle & Cascading Expiration (Design Decision #10)
    // =========================================================================

    @Test
    @DisplayName("Toggle Availability: Turning unavailable cascades PENDING matches to EXPIRED")
    void testToggleAvailability_ToUnavailable_CascadesPendingMatchesToExpired() {
        Long donorUserId = 5L;
        DonorProfile profile = DonorProfile.builder()
                .id(1L)
                .availableToDonate(true)
                .build();

        Match pendingMatch1 = Match.builder().id(101L).status(MatchStatus.PENDING).build();
        Match pendingMatch2 = Match.builder().id(102L).status(MatchStatus.PENDING).build();

        when(donorProfileRepository.findByUserId(donorUserId)).thenReturn(Optional.of(profile));
        when(donorProfileRepository.save(any(DonorProfile.class))).thenAnswer(i -> i.getArgument(0));
        when(matchRepository.findByDonorIdAndStatus(donorUserId, MatchStatus.PENDING))
                .thenReturn(List.of(pendingMatch1, pendingMatch2));

        DonorProfile updated = donorService.toggleAvailability(donorUserId, false);

        assertThat(updated.isAvailableToDonate()).isFalse();
        assertThat(pendingMatch1.getStatus()).isEqualTo(MatchStatus.EXPIRED);
        assertThat(pendingMatch2.getStatus()).isEqualTo(MatchStatus.EXPIRED);
        verify(matchRepository).saveAll(List.of(pendingMatch1, pendingMatch2));
    }

    @Test
    @DisplayName("Toggle Availability: Turning available does not alter existing matches")
    void testToggleAvailability_ToAvailable_DoesNotExpireMatches() {
        Long donorUserId = 5L;
        DonorProfile profile = DonorProfile.builder()
                .id(1L)
                .availableToDonate(false)
                .build();

        when(donorProfileRepository.findByUserId(donorUserId)).thenReturn(Optional.of(profile));
        when(donorProfileRepository.save(any(DonorProfile.class))).thenAnswer(i -> i.getArgument(0));

        DonorProfile updated = donorService.toggleAvailability(donorUserId, true);

        assertThat(updated.isAvailableToDonate()).isTrue();
        verify(matchRepository, never()).findByDonorIdAndStatus(any(), any());
        verify(matchRepository, never()).saveAll(any());
    }
}
