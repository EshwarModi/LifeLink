package com.lifelink.service;

import com.lifelink.model.DonorProfile;
import com.lifelink.model.Match;
import com.lifelink.model.SeekerRequest;
import com.lifelink.model.User;
import com.lifelink.model.enums.MatchStatus;
import com.lifelink.model.enums.RequestStatus;
import com.lifelink.model.enums.UrgencyLevel;
import com.lifelink.model.enums.UserType;
import com.lifelink.repository.DonorProfileRepository;
import com.lifelink.repository.MatchRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MatchingServiceTest {

    @Mock
    private DonorProfileRepository donorProfileRepository;

    @Mock
    private MatchRepository matchRepository;

    @InjectMocks
    private MatchingService matchingService;

    private User seekerUser;
    private User donorUser;
    private DonorProfile donorProfile;
    private SeekerRequest seekerRequest;

    @BeforeEach
    void setUp() {
        seekerUser = User.builder()
                .id(100L)
                .email("seeker@test.com")
                .fullName("Jane Seeker")
                .phone("9876543210")
                .userType(UserType.SEEKER)
                .build();

        donorUser = User.builder()
                .id(200L)
                .email("donor@test.com")
                .fullName("John Donor")
                .phone("9123456789")
                .userType(UserType.DONOR)
                .build();

        donorProfile = DonorProfile.builder()
                .id(1L)
                .user(donorUser)
                .bloodGroup("A+")
                .age(25)
                .gender("Male")
                .isAvailable(true)
                .build();

        seekerRequest = SeekerRequest.builder()
                .id(10L)
                .seeker(seekerUser)
                .bloodGroup("A+")
                .units(2)
                .urgency(UrgencyLevel.HIGH)
                .hospitalName("City Hospital")
                .hospitalAddress("Main St")
                .requiredByDate(LocalDate.now().plusDays(2))
                .status(RequestStatus.OPEN)
                .build();
    }

    @Test
    @DisplayName("Should create PENDING match when donor has exact matching blood group")
    void createMatchesForRequest_ExactBloodGroupMatch_CreatesPendingMatch() {
        when(donorProfileRepository.findByBloodGroupAndIsAvailableTrue("A+"))
                .thenReturn(List.of(donorProfile));
        when(matchRepository.existsBySeekerRequestAndDonor(seekerRequest, donorUser))
                .thenReturn(false);
        when(matchRepository.save(any(Match.class))).thenAnswer(invocation -> invocation.getArgument(0));

        List<Match> matches = matchingService.createMatchesForRequest(seekerRequest);

        assertEquals(1, matches.size());
        Match match = matches.get(0);
        assertEquals(seekerRequest, match.getSeekerRequest());
        assertEquals(donorUser, match.getDonor());
        assertEquals(MatchStatus.PENDING, match.getStatus());
        assertFalse(match.getContactShared());

        verify(matchRepository, times(1)).save(any(Match.class));
    }

    @Test
    @DisplayName("Should accept match and reveal contact info when legitimate donor accepts")
    void acceptMatch_ValidDonorOwnership_FlipsStatusAndRevealsContact() {
        Match existingMatch = Match.builder()
                .id(50L)
                .seekerRequest(seekerRequest)
                .donor(donorUser)
                .status(MatchStatus.PENDING)
                .contactShared(false)
                .build();

        when(matchRepository.findById(50L)).thenReturn(Optional.of(existingMatch));
        when(matchRepository.save(any(Match.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Match acceptedMatch = matchingService.acceptMatch(50L, donorUser);

        assertEquals(MatchStatus.ACCEPTED, acceptedMatch.getStatus());
        assertTrue(acceptedMatch.getContactShared());
    }

    @Test
    @DisplayName("Should throw SecurityException when unauthorized user attempts to accept match")
    void acceptMatch_UnauthorizedUser_ThrowsSecurityException() {
        User sneakyUser = User.builder()
                .id(999L)
                .email("sneaky@test.com")
                .userType(UserType.DONOR)
                .build();

        Match existingMatch = Match.builder()
                .id(50L)
                .seekerRequest(seekerRequest)
                .donor(donorUser)
                .status(MatchStatus.PENDING)
                .contactShared(false)
                .build();

        when(matchRepository.findById(50L)).thenReturn(Optional.of(existingMatch));

        assertThrows(SecurityException.class, () -> matchingService.acceptMatch(50L, sneakyUser));
        verify(matchRepository, never()).save(any(Match.class));
    }
}
