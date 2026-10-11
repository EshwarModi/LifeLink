package com.lifelink.service;

import com.lifelink.dto.SeekerRequestDto;
import com.lifelink.model.SeekerRequest;
import com.lifelink.model.User;
import com.lifelink.model.enums.BloodGroup;
import com.lifelink.model.enums.MatchStatus;
import com.lifelink.model.enums.RequestStatus;
import com.lifelink.model.enums.Urgency;
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
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pure JUnit 5 + Mockito unit tests for {@link SeekerRequestService}.
 *
 * Verifies Design Decision #6:
 * Identical validation rules for create and edit request (required-by must be
 * in the future in both, units between 1-20, non-blank hospital details),
 * plus ownership and request lifecycle checks.
 */
@ExtendWith(MockitoExtension.class)
class SeekerRequestServiceUnitTest {

    @Mock
    private SeekerRequestRepository seekerRequestRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private MatchRepository matchRepository;

    @Mock
    private MatchingService matchingService;

    @InjectMocks
    private SeekerRequestService seekerRequestService;

    private User seekerOwner;
    private User otherSeeker;
    private SeekerRequest existingOpenRequest;

    @BeforeEach
    void setUp() {
        seekerOwner = User.builder()
                .id(1L)
                .username("seeker_owner")
                .city("Mumbai")
                .state("Maharashtra")
                .userType(UserType.SEEKER)
                .build();

        otherSeeker = User.builder()
                .id(2L)
                .username("other_seeker")
                .userType(UserType.SEEKER)
                .build();

        existingOpenRequest = SeekerRequest.builder()
                .id(100L)
                .seeker(seekerOwner)
                .bloodGroup(BloodGroup.A_POSITIVE)
                .unitsNeeded(2)
                .urgency(Urgency.MEDIUM)
                .hospitalName("City Hospital")
                .hospitalAddress("City Road 1")
                .requiredBy(LocalDate.now().plusDays(5))
                .status(RequestStatus.OPEN)
                .build();
    }

    private SeekerRequestDto.SeekerRequestDtoBuilder validDtoBuilder() {
        return SeekerRequestDto.builder()
                .bloodGroup(BloodGroup.A_POSITIVE)
                .unitsNeeded(2)
                .urgency(Urgency.MEDIUM)
                .reason("Scheduled Surgery")
                .hospitalName("City Hospital")
                .hospitalAddress("City Road 1")
                .requiredBy(LocalDate.now().plusDays(5));
    }

    // =========================================================================
    // Validation Parity: Null Blood Group
    // =========================================================================

    @Test
    @DisplayName("Validation Parity: Create request rejects null blood group")
    void testCreate_NullBloodGroup_ThrowsIllegalArgumentException() {
        SeekerRequestDto dto = validDtoBuilder().bloodGroup(null).build();

        assertThatThrownBy(() -> seekerRequestService.createRequest(1L, dto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Blood group is required");
    }

    @Test
    @DisplayName("Validation Parity: Edit request rejects null blood group")
    void testEdit_NullBloodGroup_ThrowsIllegalArgumentException() {
        SeekerRequestDto dto = validDtoBuilder().bloodGroup(null).build();

        assertThatThrownBy(() -> seekerRequestService.editRequest(1L, 100L, dto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Blood group is required");
    }

    // =========================================================================
    // Validation Parity: Units Needed Range (1 - 20)
    // =========================================================================

    @Test
    @DisplayName("Validation Parity: Create request rejects unitsNeeded < 1 (0 or negative)")
    void testCreate_UnitsNeededLessThanOne_ThrowsIllegalArgumentException() {
        SeekerRequestDto dtoZero = validDtoBuilder().unitsNeeded(0).build();
        SeekerRequestDto dtoNegative = validDtoBuilder().unitsNeeded(-3).build();

        assertThatThrownBy(() -> seekerRequestService.createRequest(1L, dtoZero))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("between 1 and 20");

        assertThatThrownBy(() -> seekerRequestService.createRequest(1L, dtoNegative))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("between 1 and 20");
    }

    @Test
    @DisplayName("Validation Parity: Edit request rejects unitsNeeded < 1 (0 or negative)")
    void testEdit_UnitsNeededLessThanOne_ThrowsIllegalArgumentException() {
        SeekerRequestDto dtoZero = validDtoBuilder().unitsNeeded(0).build();

        assertThatThrownBy(() -> seekerRequestService.editRequest(1L, 100L, dtoZero))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("between 1 and 20");
    }

    @Test
    @DisplayName("Validation Parity: Create request rejects unitsNeeded > 20")
    void testCreate_UnitsNeededGreaterThanTwenty_ThrowsIllegalArgumentException() {
        SeekerRequestDto dto = validDtoBuilder().unitsNeeded(21).build();

        assertThatThrownBy(() -> seekerRequestService.createRequest(1L, dto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("between 1 and 20");
    }

    @Test
    @DisplayName("Validation Parity: Edit request rejects unitsNeeded > 20")
    void testEdit_UnitsNeededGreaterThanTwenty_ThrowsIllegalArgumentException() {
        SeekerRequestDto dto = validDtoBuilder().unitsNeeded(25).build();

        assertThatThrownBy(() -> seekerRequestService.editRequest(1L, 100L, dto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("between 1 and 20");
    }

    // =========================================================================
    // Validation Parity: RequiredBy Date strictly in the future
    // =========================================================================

    @Test
    @DisplayName("Validation Parity: Create request rejects requiredBy in past or today")
    void testCreate_RequiredByPastOrToday_ThrowsIllegalArgumentException() {
        SeekerRequestDto pastDto = validDtoBuilder().requiredBy(LocalDate.now().minusDays(1)).build();
        SeekerRequestDto todayDto = validDtoBuilder().requiredBy(LocalDate.now()).build();

        assertThatThrownBy(() -> seekerRequestService.createRequest(1L, pastDto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("strictly in the future");

        assertThatThrownBy(() -> seekerRequestService.createRequest(1L, todayDto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("strictly in the future");
    }

    @Test
    @DisplayName("Validation Parity: Edit request rejects requiredBy in past or today")
    void testEdit_RequiredByPastOrToday_ThrowsIllegalArgumentException() {
        SeekerRequestDto pastDto = validDtoBuilder().requiredBy(LocalDate.now().minusDays(1)).build();
        SeekerRequestDto todayDto = validDtoBuilder().requiredBy(LocalDate.now()).build();

        assertThatThrownBy(() -> seekerRequestService.editRequest(1L, 100L, pastDto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("strictly in the future");

        assertThatThrownBy(() -> seekerRequestService.editRequest(1L, 100L, todayDto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("strictly in the future");
    }

    // =========================================================================
    // Validation Parity: Hospital Details Non-Blank
    // =========================================================================

    @Test
    @DisplayName("Validation Parity: Create request rejects blank hospital name or address")
    void testCreate_BlankHospitalDetails_ThrowsIllegalArgumentException() {
        SeekerRequestDto blankName = validDtoBuilder().hospitalName("   ").build();
        SeekerRequestDto blankAddress = validDtoBuilder().hospitalAddress("").build();

        assertThatThrownBy(() -> seekerRequestService.createRequest(1L, blankName))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Hospital name is required");

        assertThatThrownBy(() -> seekerRequestService.createRequest(1L, blankAddress))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Hospital address is required");
    }

    @Test
    @DisplayName("Validation Parity: Edit request rejects blank hospital name or address")
    void testEdit_BlankHospitalDetails_ThrowsIllegalArgumentException() {
        SeekerRequestDto blankName = validDtoBuilder().hospitalName("   ").build();
        SeekerRequestDto blankAddress = validDtoBuilder().hospitalAddress("").build();

        assertThatThrownBy(() -> seekerRequestService.editRequest(1L, 100L, blankName))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Hospital name is required");

        assertThatThrownBy(() -> seekerRequestService.editRequest(1L, 100L, blankAddress))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Hospital address is required");
    }

    // =========================================================================
    // Edit Ownership and State Transition Checks
    // =========================================================================

    @Test
    @DisplayName("Edit Ownership: Throws UnauthorizedActionException when user edits another seeker's request")
    void testEdit_OwnershipCheckFails_ThrowsUnauthorizedActionException() {
        SeekerRequestDto validDto = validDtoBuilder().build();
        when(seekerRequestRepository.findById(100L)).thenReturn(Optional.of(existingOpenRequest));

        // Caller user is 2L, but request owner is 1L
        assertThatThrownBy(() -> seekerRequestService.editRequest(2L, 100L, validDto))
                .isInstanceOf(UnauthorizedActionException.class)
                .hasMessageContaining("You can only edit your own requests");
    }

    @Test
    @DisplayName("Edit State: Throws InvalidRequestStateException when request is not OPEN (e.g. FULFILLED)")
    void testEdit_NonOpenRequest_ThrowsInvalidRequestStateException() {
        SeekerRequest fulfilledRequest = SeekerRequest.builder()
                .id(100L)
                .seeker(seekerOwner)
                .status(RequestStatus.FULFILLED)
                .build();

        SeekerRequestDto validDto = validDtoBuilder().build();
        when(seekerRequestRepository.findById(100L)).thenReturn(Optional.of(fulfilledRequest));

        assertThatThrownBy(() -> seekerRequestService.editRequest(1L, 100L, validDto))
                .isInstanceOf(InvalidRequestStateException.class)
                .hasMessageContaining("Cannot edit request with status: FULFILLED");
    }

    @Test
    @DisplayName("Edit Success: Updates fields, deletes PENDING matches, and triggers matching if blood group was changed")
    void testEdit_BloodGroupChanged_DeletesPendingMatchesAndTriggersMatching() {
        SeekerRequestDto dtoWithNewBloodGroup = validDtoBuilder()
                .bloodGroup(BloodGroup.B_POSITIVE) // Changed from A_POSITIVE
                .unitsNeeded(4)
                .build();

        when(seekerRequestRepository.findById(100L)).thenReturn(Optional.of(existingOpenRequest));
        when(matchRepository.existsByRequestIdAndStatus(100L, MatchStatus.ACCEPTED)).thenReturn(false);
        when(matchRepository.deleteByRequestIdAndStatus(100L, MatchStatus.PENDING)).thenReturn(3L);
        when(seekerRequestRepository.save(any(SeekerRequest.class))).thenAnswer(i -> i.getArgument(0));

        SeekerRequest updated = seekerRequestService.editRequest(1L, 100L, dtoWithNewBloodGroup);

        assertThat(updated.getBloodGroup()).isEqualTo(BloodGroup.B_POSITIVE);
        assertThat(updated.getUnitsNeeded()).isEqualTo(4);
        verify(matchRepository, times(1)).deleteByRequestIdAndStatus(100L, MatchStatus.PENDING);
        verify(matchRepository, times(1)).flush();
        verify(matchingService, times(1)).findAndCreateMatches(updated);
    }

    @Test
    @DisplayName("Edit Rejected: Throws InvalidRequestStateException when blood group changes after a donor has ACCEPTED")
    void testEdit_BloodGroupChangedWithAcceptedMatch_ThrowsInvalidRequestStateException() {
        SeekerRequestDto dtoWithNewBloodGroup = validDtoBuilder()
                .bloodGroup(BloodGroup.B_POSITIVE)
                .build();

        when(seekerRequestRepository.findById(100L)).thenReturn(Optional.of(existingOpenRequest));
        when(matchRepository.existsByRequestIdAndStatus(100L, MatchStatus.ACCEPTED)).thenReturn(true);

        assertThatThrownBy(() -> seekerRequestService.editRequest(1L, 100L, dtoWithNewBloodGroup))
                .isInstanceOf(InvalidRequestStateException.class)
                .hasMessage("Blood group cannot be changed after a donor has accepted");

        verify(seekerRequestRepository, never()).save(any());
        verify(matchRepository, never()).deleteByRequestIdAndStatus(any(), any());
        verify(matchingService, never()).findAndCreateMatches(any());
    }

    @Test
    @DisplayName("Edit Success: Does not re-trigger matching if blood group remained identical")
    void testEdit_BloodGroupUnchanged_DoesNotTriggerMatching() {
        SeekerRequestDto dtoSameBloodGroup = validDtoBuilder()
                .bloodGroup(BloodGroup.A_POSITIVE) // Same as existing
                .unitsNeeded(3)
                .build();

        when(seekerRequestRepository.findById(100L)).thenReturn(Optional.of(existingOpenRequest));
        when(seekerRequestRepository.save(any(SeekerRequest.class))).thenAnswer(i -> i.getArgument(0));

        SeekerRequest updated = seekerRequestService.editRequest(1L, 100L, dtoSameBloodGroup);

        assertThat(updated.getUnitsNeeded()).isEqualTo(3);
        verify(matchRepository, never()).deleteByRequestIdAndStatus(any(), any());
        verify(matchingService, never()).findAndCreateMatches(any());
    }

    @Test
    @DisplayName("Mark Fulfilled: Throws UnauthorizedActionException if caller is not the owner")
    void testMarkFulfilled_NonOwner_ThrowsUnauthorizedActionException() {
        when(seekerRequestRepository.findById(100L)).thenReturn(Optional.of(existingOpenRequest));

        assertThatThrownBy(() -> seekerRequestService.markFulfilled(2L, 100L))
                .isInstanceOf(UnauthorizedActionException.class);
    }

    @Test
    @DisplayName("Mark Fulfilled: Successfully sets status to FULFILLED for owner")
    void testMarkFulfilled_Success() {
        when(seekerRequestRepository.findById(100L)).thenReturn(Optional.of(existingOpenRequest));
        when(seekerRequestRepository.save(any(SeekerRequest.class))).thenAnswer(i -> i.getArgument(0));

        SeekerRequest fulfilled = seekerRequestService.markFulfilled(1L, 100L);

        assertThat(fulfilled.getStatus()).isEqualTo(RequestStatus.FULFILLED);
    }

    @Test
    @DisplayName("Mark Cancelled: Successfully sets status to CANCELLED for owner")
    void testMarkCancelled_Success() {
        when(seekerRequestRepository.findById(100L)).thenReturn(Optional.of(existingOpenRequest));
        when(seekerRequestRepository.save(any(SeekerRequest.class))).thenAnswer(i -> i.getArgument(0));

        SeekerRequest cancelled = seekerRequestService.markCancelled(1L, 100L);

        assertThat(cancelled.getStatus()).isEqualTo(RequestStatus.CANCELLED);
    }
}
