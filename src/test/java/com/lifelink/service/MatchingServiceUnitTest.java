package com.lifelink.service;

import com.lifelink.config.MatchingMode;
import com.lifelink.config.MatchingProperties;
import com.lifelink.model.DonorProfile;
import com.lifelink.model.Match;
import com.lifelink.model.SeekerRequest;
import com.lifelink.model.User;
import com.lifelink.model.enums.BloodGroup;
import com.lifelink.model.enums.MatchStatus;
import com.lifelink.model.enums.RequestStatus;
import com.lifelink.model.enums.UserType;
import com.lifelink.repository.MatchRepository;
import com.lifelink.repository.SeekerRequestRepository;
import com.lifelink.repository.UserRepository;
import com.lifelink.service.exception.InvalidRequestStateException;
import com.lifelink.service.exception.UnauthorizedActionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pure JUnit 5 + Mockito unit tests for {@link MatchingService}.
 *
 * Covers:
 * 1. Location-aware three-tier matching fallback (Tier 1 city -> Tier 2 state -> Tier 3 broad).
 * 2. Donor eligibility checks during match acceptance.
 * 3. Ownership enforcement (donor can only act on their own matches; seeker cannot accept own request).
 * 4. Phase 12 Feature Flag: EXACT mode (default) vs COMPATIBLE mode (ABO/Rh red cell compatibility).
 */
@ExtendWith(MockitoExtension.class)
class MatchingServiceUnitTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private SeekerRequestRepository seekerRequestRepository;

    @Mock
    private MatchRepository matchRepository;

    @Mock
    private NotificationService notificationService;

    @Mock
    private DonorService donorService;

    @Spy
    private MatchingProperties matchingProperties = new MatchingProperties();

    @InjectMocks
    private MatchingService matchingService;

    private User seeker;
    private SeekerRequest openRequest;
    private User donor1;
    private User donor2;
    private User donor3;

    @BeforeEach
    void setUp() {
        matchingProperties.setMode(MatchingMode.EXACT);

        seeker = User.builder()
                .id(1L)
                .username("seeker_john")
                .city("Pune")
                .state("Maharashtra")
                .userType(UserType.SEEKER)
                .build();

        openRequest = SeekerRequest.builder()
                .id(100L)
                .seeker(seeker)
                .bloodGroup(BloodGroup.O_POSITIVE)
                .unitsNeeded(2)
                .requiredBy(LocalDate.now().plusDays(5))
                .status(RequestStatus.OPEN)
                .build();

        donor1 = User.builder().id(11L).username("donor_pune_1").city("Pune").state("Maharashtra").build();
        donor2 = User.builder().id(12L).username("donor_pune_2").city("Pune").state("Maharashtra").build();
        donor3 = User.builder().id(13L).username("donor_pune_3").city("Pune").state("Maharashtra").build();
    }

    // =========================================================================
    // Tier Fallback Tests (Design Decision #8)
    // =========================================================================

    @Test
    @DisplayName("Tier Fallback: Tier 1 finds >= 3 donors, so Tier 2 and Tier 3 are never queried")
    void testTierFallback_Tier1Sufficient() {
        // Given Tier 1 returns 3 donors (meeting TARGET_MATCH_THRESHOLD = 3)
        when(userRepository.findEligibleDonorsByBloodGroupsAndCity(
                eq(List.of(BloodGroup.O_POSITIVE)), eq("Pune"), any(LocalDate.class), eq(1L)))
                .thenReturn(List.of(donor1, donor2, donor3));

        when(matchRepository.existsByRequestIdAndDonorId(anyLong(), anyLong())).thenReturn(false);
        when(matchRepository.save(any(Match.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        List<Match> matches = matchingService.findAndCreateMatches(openRequest);

        // Then
        assertThat(matches).hasSize(3);
        verify(userRepository, times(1)).findEligibleDonorsByBloodGroupsAndCity(any(), any(), any(), any());
        verify(userRepository, never()).findEligibleDonorsByBloodGroupsAndStateExcludingCity(any(), any(), any(), any(), any());
        verify(userRepository, never()).findEligibleDonorsByBloodGroupsBroadExcludingState(any(), any(), any(), any());
        verify(notificationService, times(3)).sendMatchNotification(any(User.class), eq(openRequest));
    }

    @Test
    @DisplayName("Tier Fallback: Tier 1 finds 1 donor (< 3), expands to Tier 2 (State) which fills the threshold")
    void testTierFallback_Tier1Insufficient_ExpandsToTier2() {
        User donorState1 = User.builder().id(21L).username("donor_mumbai").city("Mumbai").state("Maharashtra").build();
        User donorState2 = User.builder().id(22L).username("donor_nagpur").city("Nagpur").state("Maharashtra").build();

        // Tier 1 returns 1 donor
        when(userRepository.findEligibleDonorsByBloodGroupsAndCity(
                eq(List.of(BloodGroup.O_POSITIVE)), eq("Pune"), any(LocalDate.class), eq(1L)))
                .thenReturn(List.of(donor1));

        // Tier 2 returns 2 more donors from same state
        when(userRepository.findEligibleDonorsByBloodGroupsAndStateExcludingCity(
                eq(List.of(BloodGroup.O_POSITIVE)), eq("Maharashtra"), eq("Pune"), any(LocalDate.class), eq(1L)))
                .thenReturn(List.of(donorState1, donorState2));

        when(matchRepository.existsByRequestIdAndDonorId(anyLong(), anyLong())).thenReturn(false);
        when(matchRepository.save(any(Match.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        List<Match> matches = matchingService.findAndCreateMatches(openRequest);

        // Then
        assertThat(matches).hasSize(3);
        verify(userRepository, times(1)).findEligibleDonorsByBloodGroupsAndCity(any(), any(), any(), any());
        verify(userRepository, times(1)).findEligibleDonorsByBloodGroupsAndStateExcludingCity(any(), any(), any(), any(), any());
        verify(userRepository, never()).findEligibleDonorsByBloodGroupsBroadExcludingState(any(), any(), any(), any());
        verify(notificationService, times(3)).sendMatchNotification(any(User.class), eq(openRequest));
    }

    @Test
    @DisplayName("Tier Fallback: Tier 1 (1 donor) and Tier 2 (1 donor) < 3, expands to Tier 3 (Nationwide)")
    void testTierFallback_Tier1And2Insufficient_ExpandsToTier3() {
        User donorState1 = User.builder().id(21L).username("donor_mumbai").city("Mumbai").state("Maharashtra").build();
        User donorNationwide1 = User.builder().id(31L).username("donor_delhi").city("Delhi").state("Delhi").build();
        User donorNationwide2 = User.builder().id(32L).username("donor_bangalore").city("Bengaluru").state("Karnataka").build();

        // Tier 1 returns 1
        when(userRepository.findEligibleDonorsByBloodGroupsAndCity(
                eq(List.of(BloodGroup.O_POSITIVE)), eq("Pune"), any(LocalDate.class), eq(1L)))
                .thenReturn(List.of(donor1));

        // Tier 2 returns 1
        when(userRepository.findEligibleDonorsByBloodGroupsAndStateExcludingCity(
                eq(List.of(BloodGroup.O_POSITIVE)), eq("Maharashtra"), eq("Pune"), any(LocalDate.class), eq(1L)))
                .thenReturn(List.of(donorState1));

        // Tier 3 returns 2 nationwide donors
        when(userRepository.findEligibleDonorsByBloodGroupsBroadExcludingState(
                eq(List.of(BloodGroup.O_POSITIVE)), eq("Maharashtra"), any(LocalDate.class), eq(1L)))
                .thenReturn(List.of(donorNationwide1, donorNationwide2));

        when(matchRepository.existsByRequestIdAndDonorId(anyLong(), anyLong())).thenReturn(false);
        when(matchRepository.save(any(Match.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        List<Match> matches = matchingService.findAndCreateMatches(openRequest);

        // Then: Total capped at TARGET_MATCH_THRESHOLD (3)
        assertThat(matches).hasSize(3);
        verify(userRepository, times(1)).findEligibleDonorsByBloodGroupsAndCity(any(), any(), any(), any());
        verify(userRepository, times(1)).findEligibleDonorsByBloodGroupsAndStateExcludingCity(any(), any(), any(), any(), any());
        verify(userRepository, times(1)).findEligibleDonorsByBloodGroupsBroadExcludingState(any(), any(), any(), any());
    }

    @Test
    @DisplayName("Tier Fallback: Non-OPEN request returns empty list immediately without querying repositories")
    void testTierFallback_NonOpenRequest_ReturnsEmpty() {
        SeekerRequest fulfilledRequest = SeekerRequest.builder()
                .id(200L)
                .seeker(seeker)
                .bloodGroup(BloodGroup.O_POSITIVE)
                .status(RequestStatus.FULFILLED)
                .build();

        List<Match> matches = matchingService.findAndCreateMatches(fulfilledRequest);

        assertThat(matches).isEmpty();
        verify(userRepository, never()).findEligibleDonorsByBloodGroupsAndCity(any(), any(), any(), any());
    }

    @Test
    @DisplayName("Tier Fallback: Deduplication ensures same donor is not added twice across tiers")
    void testTierFallback_DeduplicationAcrossTiers() {
        // Tier 1 returns donor1
        when(userRepository.findEligibleDonorsByBloodGroupsAndCity(
                eq(List.of(BloodGroup.O_POSITIVE)), eq("Pune"), any(LocalDate.class), eq(1L)))
                .thenReturn(List.of(donor1));

        // Tier 2 also returns donor1 (edge case / query overlap) plus donor2
        when(userRepository.findEligibleDonorsByBloodGroupsAndStateExcludingCity(
                eq(List.of(BloodGroup.O_POSITIVE)), eq("Maharashtra"), eq("Pune"), any(LocalDate.class), eq(1L)))
                .thenReturn(List.of(donor1, donor2));

        // Tier 3 returns donor3
        when(userRepository.findEligibleDonorsByBloodGroupsBroadExcludingState(
                eq(List.of(BloodGroup.O_POSITIVE)), eq("Maharashtra"), any(LocalDate.class), eq(1L)))
                .thenReturn(List.of(donor3));

        when(matchRepository.existsByRequestIdAndDonorId(anyLong(), anyLong())).thenReturn(false);
        when(matchRepository.save(any(Match.class))).thenAnswer(invocation -> invocation.getArgument(0));

        List<Match> matches = matchingService.findAndCreateMatches(openRequest);

        assertThat(matches).hasSize(3);
        List<Long> matchedDonorIds = matches.stream().map(m -> m.getDonor().getId()).toList();
        assertThat(matchedDonorIds).containsExactly(11L, 12L, 13L);
    }

    // =========================================================================
    // Phase 12: EXACT vs COMPATIBLE Mode Tests
    // =========================================================================

    @Test
    @DisplayName("EXACT Mode (Default): When exact donors are insufficient, does NOT expand to compatible groups")
    void testExactMode_DoesNotQueryCompatibleDonors() {
        matchingProperties.setMode(MatchingMode.EXACT);

        // Tier 1 returns 1 donor
        when(userRepository.findEligibleDonorsByBloodGroupsAndCity(
                eq(List.of(BloodGroup.O_POSITIVE)), eq("Pune"), any(LocalDate.class), eq(1L)))
                .thenReturn(List.of(donor1));
        // Tier 2 & 3 return empty
        when(userRepository.findEligibleDonorsByBloodGroupsAndStateExcludingCity(anyCollection(), any(), any(), any(), any()))
                .thenReturn(List.of());
        when(userRepository.findEligibleDonorsByBloodGroupsBroadExcludingState(anyCollection(), any(), any(), any()))
                .thenReturn(List.of());

        when(matchRepository.existsByRequestIdAndDonorId(anyLong(), anyLong())).thenReturn(false);
        when(matchRepository.save(any(Match.class))).thenAnswer(i -> i.getArgument(0));

        List<Match> matches = matchingService.findAndCreateMatches(openRequest);

        assertThat(matches).hasSize(1);
        // Repository should have ONLY been called for exact blood group [O_POSITIVE]
        verify(userRepository, times(1)).findEligibleDonorsByBloodGroupsAndCity(eq(List.of(BloodGroup.O_POSITIVE)), any(), any(), any());
    }

    @Test
    @DisplayName("COMPATIBLE Mode: Ranks exact blood group matches first, then expands to compatible groups in tier order")
    void testCompatibleMode_RanksExactFirst_ThenExpandsToCompatibleDonors() {
        matchingProperties.setMode(MatchingMode.COMPATIBLE);

        // Request requires A_POSITIVE
        SeekerRequest aPosRequest = SeekerRequest.builder()
                .id(300L)
                .seeker(seeker)
                .bloodGroup(BloodGroup.A_POSITIVE)
                .unitsNeeded(2)
                .status(RequestStatus.OPEN)
                .build();

        User exactDonorA = User.builder().id(41L).username("donor_exact_a").city("Pune").state("Maharashtra").build();
        User compDonorONeg = User.builder().id(42L).username("donor_o_neg").city("Pune").state("Maharashtra").build();
        User compDonorOPos = User.builder().id(43L).username("donor_o_pos").city("Mumbai").state("Maharashtra").build();

        // Phase 1 (Exact A+): Tier 1 city returns 1 exact donor; Tier 2 & Tier 3 return empty
        when(userRepository.findEligibleDonorsByBloodGroupsAndCity(
                eq(List.of(BloodGroup.A_POSITIVE)), eq("Pune"), any(LocalDate.class), eq(1L)))
                .thenReturn(List.of(exactDonorA));
        when(userRepository.findEligibleDonorsByBloodGroupsAndStateExcludingCity(
                eq(List.of(BloodGroup.A_POSITIVE)), eq("Maharashtra"), eq("Pune"), any(LocalDate.class), eq(1L)))
                .thenReturn(List.of());
        when(userRepository.findEligibleDonorsByBloodGroupsBroadExcludingState(
                eq(List.of(BloodGroup.A_POSITIVE)), eq("Maharashtra"), any(LocalDate.class), eq(1L)))
                .thenReturn(List.of());

        // Phase 2 (Compatible non-exact for A+: A-, O+, O-):
        // Tier 1 (City) returns O- donor
        List<BloodGroup> expectedCompatibleGroups = List.of(BloodGroup.A_NEGATIVE, BloodGroup.O_POSITIVE, BloodGroup.O_NEGATIVE);
        when(userRepository.findEligibleDonorsByBloodGroupsAndCity(
                eq(expectedCompatibleGroups), eq("Pune"), any(LocalDate.class), eq(1L)))
                .thenReturn(List.of(compDonorONeg));

        // Tier 2 (State) returns O+ donor
        when(userRepository.findEligibleDonorsByBloodGroupsAndStateExcludingCity(
                eq(expectedCompatibleGroups), eq("Maharashtra"), eq("Pune"), any(LocalDate.class), eq(1L)))
                .thenReturn(List.of(compDonorOPos));

        when(matchRepository.existsByRequestIdAndDonorId(anyLong(), anyLong())).thenReturn(false);
        when(matchRepository.save(any(Match.class))).thenAnswer(i -> i.getArgument(0));

        List<Match> matches = matchingService.findAndCreateMatches(aPosRequest);

        // Then: Total is 3, and exact match is strictly first!
        assertThat(matches).hasSize(3);
        assertThat(matches.get(0).getDonor().getId()).isEqualTo(41L); // Exact match first
        assertThat(matches.get(1).getDonor().getId()).isEqualTo(42L); // Compatible tier 1
        assertThat(matches.get(2).getDonor().getId()).isEqualTo(43L); // Compatible tier 2
    }

    @Test
    @DisplayName("COMPATIBLE Mode: Allows compatible donor (O-) to accept request for A+")
    void testCompatibleMode_AcceptMatch_SuccessForCompatibleDonor() {
        matchingProperties.setMode(MatchingMode.COMPATIBLE);

        SeekerRequest aPosRequest = SeekerRequest.builder()
                .id(300L)
                .seeker(seeker)
                .bloodGroup(BloodGroup.A_POSITIVE)
                .unitsNeeded(2)
                .status(RequestStatus.OPEN)
                .build();

        DonorProfile oNegProfile = DonorProfile.builder()
                .bloodGroup(BloodGroup.O_NEGATIVE)
                .availableToDonate(true)
                .build();
        User oNegDonor = User.builder().id(77L).username("o_neg_donor").donorProfile(oNegProfile).build();

        when(seekerRequestRepository.findById(300L)).thenReturn(Optional.of(aPosRequest));
        when(userRepository.findById(77L)).thenReturn(Optional.of(oNegDonor));
        when(donorService.isDonorEligible(oNegProfile)).thenReturn(true);
        when(matchRepository.findByRequestIdAndDonorId(300L, 77L)).thenReturn(Optional.empty());
        when(matchRepository.save(any(Match.class))).thenAnswer(i -> i.getArgument(0));

        Match accepted = matchingService.acceptMatch(300L, 77L);

        assertThat(accepted.getStatus()).isEqualTo(MatchStatus.ACCEPTED);
        assertThat(accepted.isContactShared()).isTrue();
        assertThat(accepted.isCompatibleOnlyMatch()).isTrue();
    }

    @Test
    @DisplayName("EXACT Mode: Rejects non-exact compatible donor (O-) accepting request for A+")
    void testExactMode_AcceptMatch_RejectsCompatibleNonExactDonor() {
        matchingProperties.setMode(MatchingMode.EXACT);

        SeekerRequest aPosRequest = SeekerRequest.builder()
                .id(300L)
                .seeker(seeker)
                .bloodGroup(BloodGroup.A_POSITIVE)
                .unitsNeeded(2)
                .status(RequestStatus.OPEN)
                .build();

        DonorProfile oNegProfile = DonorProfile.builder()
                .bloodGroup(BloodGroup.O_NEGATIVE)
                .availableToDonate(true)
                .build();
        User oNegDonor = User.builder().id(77L).username("o_neg_donor").donorProfile(oNegProfile).build();

        when(seekerRequestRepository.findById(300L)).thenReturn(Optional.of(aPosRequest));
        when(userRepository.findById(77L)).thenReturn(Optional.of(oNegDonor));
        when(donorService.isDonorEligible(oNegProfile)).thenReturn(true);

        assertThatThrownBy(() -> matchingService.acceptMatch(300L, 77L))
                .isInstanceOf(InvalidRequestStateException.class)
                .hasMessageContaining("does not match request");
    }

    // =========================================================================
    // Donor Eligibility Tests (Design Decision #9 & #15)
    // =========================================================================

    @Test
    @DisplayName("Eligibility: Throws InvalidRequestStateException when donor profile is null")
    void testAcceptMatch_DonorProfileNull_ThrowsException() {
        User donorWithoutProfile = User.builder().id(50L).username("no_profile").donorProfile(null).build();
        when(seekerRequestRepository.findById(100L)).thenReturn(Optional.of(openRequest));
        when(userRepository.findById(50L)).thenReturn(Optional.of(donorWithoutProfile));

        assertThatThrownBy(() -> matchingService.acceptMatch(100L, 50L))
                .isInstanceOf(InvalidRequestStateException.class)
                .hasMessageContaining("ineligible");
    }

    @Test
    @DisplayName("Eligibility: Throws InvalidRequestStateException when donorService reports donor ineligible")
    void testAcceptMatch_DonorIneligible_ThrowsException() {
        DonorProfile profile = DonorProfile.builder()
                .bloodGroup(BloodGroup.O_POSITIVE)
                .availableToDonate(false)
                .build();
        User donor = User.builder().id(51L).username("unavail_donor").donorProfile(profile).build();

        when(seekerRequestRepository.findById(100L)).thenReturn(Optional.of(openRequest));
        when(userRepository.findById(51L)).thenReturn(Optional.of(donor));
        when(donorService.isDonorEligible(profile)).thenReturn(false);

        assertThatThrownBy(() -> matchingService.acceptMatch(100L, 51L))
                .isInstanceOf(InvalidRequestStateException.class)
                .hasMessageContaining("ineligible to donate");
    }

    @Test
    @DisplayName("Eligibility: Throws InvalidRequestStateException when donor blood group does not match request")
    void testAcceptMatch_BloodGroupMismatch_ThrowsException() {
        DonorProfile profile = DonorProfile.builder()
                .bloodGroup(BloodGroup.A_POSITIVE) // Request requires O_POSITIVE
                .availableToDonate(true)
                .build();
        User donor = User.builder().id(52L).username("a_donor").donorProfile(profile).build();

        when(seekerRequestRepository.findById(100L)).thenReturn(Optional.of(openRequest));
        when(userRepository.findById(52L)).thenReturn(Optional.of(donor));
        when(donorService.isDonorEligible(profile)).thenReturn(true);

        assertThatThrownBy(() -> matchingService.acceptMatch(100L, 52L))
                .isInstanceOf(InvalidRequestStateException.class)
                .hasMessageContaining("does not match request");
    }

    // =========================================================================
    // Ownership Check Tests (Design Decision #5)
    // =========================================================================

    @Test
    @DisplayName("Ownership: Seeker cannot accept their own request as a donor")
    void testAcceptMatch_SeekerAcceptingOwnRequest_ThrowsUnauthorizedActionException() {
        when(seekerRequestRepository.findById(100L)).thenReturn(Optional.of(openRequest));
        when(userRepository.findById(1L)).thenReturn(Optional.of(seeker));

        assertThatThrownBy(() -> matchingService.acceptMatch(100L, 1L))
                .isInstanceOf(UnauthorizedActionException.class)
                .hasMessageContaining("Seekers cannot accept their own requests");
    }

    @Test
    @DisplayName("Ownership: Throws UnauthorizedActionException when accepting a match assigned to another donor")
    void testAcceptMatch_ExistingMatchAssignedToDifferentDonor_ThrowsUnauthorizedActionException() {
        DonorProfile profile = DonorProfile.builder()
                .bloodGroup(BloodGroup.O_POSITIVE)
                .availableToDonate(true)
                .build();
        User donorCaller = User.builder().id(77L).username("caller_donor").donorProfile(profile).build();
        User otherDonor = User.builder().id(88L).username("other_donor").build();

        Match existingMatch = Match.builder()
                .id(501L)
                .request(openRequest)
                .donor(otherDonor) // assigned to someone else!
                .status(MatchStatus.PENDING)
                .build();

        when(seekerRequestRepository.findById(100L)).thenReturn(Optional.of(openRequest));
        when(userRepository.findById(77L)).thenReturn(Optional.of(donorCaller));
        when(donorService.isDonorEligible(profile)).thenReturn(true);
        when(matchRepository.findByRequestIdAndDonorId(100L, 77L)).thenReturn(Optional.of(existingMatch));

        assertThatThrownBy(() -> matchingService.acceptMatch(100L, 77L))
                .isInstanceOf(UnauthorizedActionException.class)
                .hasMessageContaining("not authorized to accept a match for another donor");
    }

    @Test
    @DisplayName("Ownership: Successful acceptance updates status to ACCEPTED and discloses contact details")
    void testAcceptMatch_Success_ExistingPendingMatch() {
        DonorProfile profile = DonorProfile.builder()
                .bloodGroup(BloodGroup.O_POSITIVE)
                .availableToDonate(true)
                .build();
        User donorCaller = User.builder().id(77L).username("caller_donor").donorProfile(profile).build();

        Match pendingMatch = Match.builder()
                .id(502L)
                .request(openRequest)
                .donor(donorCaller)
                .status(MatchStatus.PENDING)
                .contactShared(false)
                .matchedAt(LocalDateTime.now().minusHours(2))
                .build();

        when(seekerRequestRepository.findById(100L)).thenReturn(Optional.of(openRequest));
        when(userRepository.findById(77L)).thenReturn(Optional.of(donorCaller));
        when(donorService.isDonorEligible(profile)).thenReturn(true);
        when(matchRepository.findByRequestIdAndDonorId(100L, 77L)).thenReturn(Optional.of(pendingMatch));
        when(matchRepository.save(any(Match.class))).thenAnswer(i -> i.getArgument(0));

        Match accepted = matchingService.acceptMatch(100L, 77L);

        assertThat(accepted.getStatus()).isEqualTo(MatchStatus.ACCEPTED);
        assertThat(accepted.isContactShared()).isTrue();
        verify(matchRepository).save(pendingMatch);
    }

    @Test
    @DisplayName("Ownership: Upsert creates and accepts match when donor browses request directory directly")
    void testAcceptMatch_Success_UpsertWhenNoPriorMatch() {
        DonorProfile profile = DonorProfile.builder()
                .bloodGroup(BloodGroup.O_POSITIVE)
                .availableToDonate(true)
                .build();
        User donorCaller = User.builder().id(77L).username("caller_donor").donorProfile(profile).build();

        when(seekerRequestRepository.findById(100L)).thenReturn(Optional.of(openRequest));
        when(userRepository.findById(77L)).thenReturn(Optional.of(donorCaller));
        when(donorService.isDonorEligible(profile)).thenReturn(true);
        when(matchRepository.findByRequestIdAndDonorId(100L, 77L)).thenReturn(Optional.empty()); // no prior match
        when(matchRepository.save(any(Match.class))).thenAnswer(i -> i.getArgument(0));

        Match accepted = matchingService.acceptMatch(100L, 77L);

        assertThat(accepted.getStatus()).isEqualTo(MatchStatus.ACCEPTED);
        assertThat(accepted.isContactShared()).isTrue();
        assertThat(accepted.getDonor()).isEqualTo(donorCaller);
        assertThat(accepted.getRequest()).isEqualTo(openRequest);
    }

    @Test
    @DisplayName("Ownership: Throws UnauthorizedActionException when declining a match assigned to another donor")
    void testDeclineMatch_OwnershipCheckFails_ThrowsUnauthorizedActionException() {
        User otherDonor = User.builder().id(99L).username("other_donor").build();
        Match match = Match.builder().id(601L).request(openRequest).donor(otherDonor).status(MatchStatus.PENDING).build();

        when(matchRepository.findByRequestIdAndDonorId(100L, 77L)).thenReturn(Optional.of(match));

        assertThatThrownBy(() -> matchingService.declineMatch(100L, 77L))
                .isInstanceOf(UnauthorizedActionException.class)
                .hasMessageContaining("cannot decline a match assigned to another donor");
    }

    @Test
    @DisplayName("Decline Match: Successful decline sets status to DECLINED and contactShared to false")
    void testDeclineMatch_Success() {
        User callerDonor = User.builder().id(77L).username("caller_donor").build();
        Match match = Match.builder().id(602L).request(openRequest).donor(callerDonor).status(MatchStatus.PENDING).contactShared(false).build();

        when(matchRepository.findByRequestIdAndDonorId(100L, 77L)).thenReturn(Optional.of(match));
        when(matchRepository.save(any(Match.class))).thenAnswer(i -> i.getArgument(0));

        Match declined = matchingService.declineMatch(100L, 77L);

        assertThat(declined.getStatus()).isEqualTo(MatchStatus.DECLINED);
        assertThat(declined.isContactShared()).isFalse();
        verify(matchRepository).save(match);
    }
}
