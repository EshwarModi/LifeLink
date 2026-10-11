package com.lifelink.notification;

import com.lifelink.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Asynchronous transactional event listener that dispatches donor match alert emails
 * strictly after the match-creation transaction has committed.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MatchNotificationListener {

    private final NotificationService notificationService;

    /**
     * Handles {@link MatchCreatedEvent} after the originating database transaction commits,
     * executing asynchronously on the dedicated {@code mailTaskExecutor} thread pool.
     *
     * <p>Failure-tolerant: any unexpected exception is logged and swallowed.</p>
     *
     * @param event immutable plain-value match notification payload
     */
    @Async("mailTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMatchCreated(MatchCreatedEvent event) {
        try {
            notificationService.sendMatchNotification(event);
        } catch (Exception ex) {
            log.error("Unexpected failure in MatchNotificationListener for donor {} (Request ID: {}): {}",
                    event != null ? event.donorEmail() : "unknown",
                    event != null ? event.requestId() : "unknown",
                    ex.getMessage());
        }
    }
}
