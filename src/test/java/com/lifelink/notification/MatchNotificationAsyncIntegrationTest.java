package com.lifelink.notification;

import com.lifelink.model.DonorProfile;
import com.lifelink.model.Match;
import com.lifelink.model.SeekerRequest;
import com.lifelink.model.User;
import com.lifelink.model.enums.BloodGroup;
import com.lifelink.model.enums.Gender;
import com.lifelink.model.enums.MatchStatus;
import com.lifelink.model.enums.RequestStatus;
import com.lifelink.model.enums.Urgency;
import com.lifelink.model.enums.UserType;
import com.lifelink.repository.DonorProfileRepository;
import com.lifelink.repository.MatchRepository;
import com.lifelink.repository.SeekerRequestRepository;
import com.lifelink.repository.UserRepository;
import com.lifelink.service.MatchingService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

/**
 * Integration tests verifying asynchronous {@code AFTER_COMMIT} match notifications:
 * <ul>
 *   <li>Matches are persisted even when {@link JavaMailSender} throws an exception.</li>
 *   <li>No email is sent and the listener is never invoked if the transaction rolls back.</li>
 *   <li>The calling (HTTP) thread returns immediately without blocking on a slow mail sender,
 *       while the notification runs on a {@code "mail-*"} thread.</li>
 *   <li>The dispatched email body contains no seeker phone number or personal contact info.</li>
 * </ul>
 */
@SpringBootTest
@ActiveProfiles("test")
class MatchNotificationAsyncIntegrationTest {

    @Autowired
    private MatchingService matchingService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DonorProfileRepository donorProfileRepository;

    @Autowired
    private SeekerRequestRepository seekerRequestRepository;

    @Autowired
    private MatchRepository matchRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @MockBean
    private JavaMailSender javaMailSender;

    @SpyBean
    private MatchNotificationListener matchNotificationListener;

    private User seeker;
    private User donor;
    private DonorProfile donorProfile;
    private SeekerRequest savedRequest;

    @BeforeEach
    void setUp() {
        reset(javaMailSender, matchNotificationListener);

        seeker = userRepository.save(User.builder()
                .username("async_seeker")
                .email("async_seeker@test.com")
                .passwordHash(passwordEncoder.encode("Secret123"))
                .fullName("Async Seeker")
                .phone("9111222333")
                .city("Hyderabad")
                .state("Telangana")
                .userType(UserType.SEEKER)
                .build());

        donor = userRepository.save(User.builder()
                .username("async_donor")
                .email("async_donor@test.com")
                .passwordHash(passwordEncoder.encode("Secret123"))
                .fullName("Async Donor")
                .phone("9444555666")
                .city("Hyderabad")
                .state("Telangana")
                .userType(UserType.DONOR)
                .build());

        donorProfile = donorProfileRepository.save(DonorProfile.builder()
                .user(donor)
                .bloodGroup(BloodGroup.B_NEGATIVE)
                .age(29)
                .gender(Gender.FEMALE)
                .availableToDonate(true)
                .lastDonationDate(LocalDate.now().minusDays(120))
                .build());
        donor.setDonorProfile(donorProfile);

        savedRequest = seekerRequestRepository.save(SeekerRequest.builder()
                .seeker(seeker)
                .bloodGroup(BloodGroup.B_NEGATIVE)
                .unitsNeeded(2)
                .urgency(Urgency.CRITICAL)
                .reason("Emergency surgery")
                .hospitalName("Apollo Hospital")
                .hospitalAddress("Jubilee Hills, Hyderabad")
                .requiredBy(LocalDate.now().plusDays(2))
                .status(RequestStatus.OPEN)
                .build());
    }

    @AfterEach
    void tearDown() {
        if (savedRequest != null && savedRequest.getId() != null) {
            List<Match> matches = matchRepository.findByRequestIdOrderByMatchedAtDesc(savedRequest.getId());
            matchRepository.deleteAll(matches);
            seekerRequestRepository.deleteById(savedRequest.getId());
        }
        if (donorProfile != null && donorProfile.getId() != null) {
            donorProfileRepository.deleteById(donorProfile.getId());
        }
        if (donor != null && donor.getId() != null) {
            userRepository.deleteById(donor.getId());
        }
        if (seeker != null && seeker.getId() != null) {
            userRepository.deleteById(seeker.getId());
        }
    }

    @Test
    @DisplayName("Async Notification: Matching still creates and commits matches when JavaMailSender throws")
    void testMatchingCreatesMatchesWhenMailSenderThrows() throws InterruptedException {
        CountDownLatch failureHandledLatch = new CountDownLatch(1);
        doAnswer(invocation -> {
            failureHandledLatch.countDown();
            throw new RuntimeException("SMTP server unreachable");
        }).when(javaMailSender).send(any(SimpleMailMessage.class));

        List<Match> created = matchingService.findAndCreateMatches(savedRequest);

        assertThat(created).hasSize(1);
        assertThat(created.get(0).getStatus()).isEqualTo(MatchStatus.PENDING);

        // Wait for async listener to invoke mailSender and swallow the exception
        boolean invoked = failureHandledLatch.await(5, TimeUnit.SECONDS);
        assertThat(invoked).isTrue();

        // Match remains committed in the database
        List<Match> persisted = matchRepository.findByRequestIdOrderByMatchedAtDesc(savedRequest.getId());
        assertThat(persisted).hasSize(1);
        assertThat(persisted.get(0).getDonor().getId()).isEqualTo(donor.getId());
    }

    @Test
    @DisplayName("Async Notification: No email is sent and listener is not invoked when transaction rolls back")
    void testNoEmailSentWhenTransactionRollsBack() throws InterruptedException {
        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
            matchingService.findAndCreateMatches(savedRequest);
            throw new IllegalStateException("Simulated downstream failure forcing rollback");
        })).isInstanceOf(IllegalStateException.class);

        // Give background executor brief window to confirm nothing fires asynchronously
        Thread.sleep(300);

        // Verify transaction rolled back matches AND listener/mailSender were never invoked
        assertThat(matchRepository.findByRequestIdOrderByMatchedAtDesc(savedRequest.getId())).isEmpty();
        verify(matchNotificationListener, never()).onMatchCreated(any(MatchCreatedEvent.class));
        verify(javaMailSender, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    @DisplayName("Async Notification: Calling thread returns without waiting on a slow JavaMailSender")
    void testCallerReturnsWithoutWaitingOnSlowMailSender() throws InterruptedException {
        CountDownLatch mailStartedLatch = new CountDownLatch(1);
        CountDownLatch mailCompletedLatch = new CountDownLatch(1);
        AtomicReference<String> mailThreadName = new AtomicReference<>();

        doAnswer(invocation -> {
            mailThreadName.set(Thread.currentThread().getName());
            mailStartedLatch.countDown();
            Thread.sleep(1_200);
            mailCompletedLatch.countDown();
            return null;
        }).when(javaMailSender).send(any(SimpleMailMessage.class));

        long startNanos = System.nanoTime();
        List<Match> matches = matchingService.findAndCreateMatches(savedRequest);
        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);

        // Caller thread must return well before the 1,200ms sleep in JavaMailSender
        assertThat(matches).hasSize(1);
        assertThat(elapsedMs).isLessThan(800L);

        // Wait for async mail delivery to finish and verify it ran on the "mail-" thread pool
        assertThat(mailStartedLatch.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(mailCompletedLatch.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(mailThreadName.get()).startsWith("mail-");
    }

    @Test
    @DisplayName("Async Notification: Email body contains hospital details and strictly no phone numbers")
    void testEmailBodyContainsNoPhoneNumber() {
        matchingService.findAndCreateMatches(savedRequest);

        ArgumentCaptor<SimpleMailMessage> messageCaptor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(javaMailSender, timeout(5_000).times(1)).send(messageCaptor.capture());

        SimpleMailMessage sent = messageCaptor.getValue();
        assertThat(sent.getTo()).containsExactly("async_donor@test.com");
        assertThat(sent.getSubject()).contains("B-");

        String body = sent.getText();
        assertThat(body)
                .contains("Async Donor")
                .contains("Apollo Hospital")
                .contains("Jubilee Hills, Hyderabad")
                .contains("Hyderabad")
                .contains("B-")
                .contains("Critical")
                .doesNotContain(seeker.getPhone())
                .doesNotContain(donor.getPhone());
    }
}
