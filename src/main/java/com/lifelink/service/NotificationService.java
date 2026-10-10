package com.lifelink.service;

import com.lifelink.model.SeekerRequest;
import com.lifelink.model.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * Service responsible for dispatching email notifications.
 *
 * <p>Enforces Design Decision #12:
 * <ul>
 *   <li>Uses {@link JavaMailSender}.</li>
 *   <li>Strictly <b>no seeker contact info</b> (e.g. phone) is included in match alert emails.</li>
 *   <li>Completely failure-tolerant: delivery exceptions are logged and suppressed so match creation is never rolled back.</li>
 * </ul>
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final JavaMailSender mailSender;

    @Value("${lifelink.mail.from:no-reply@lifelink.org}")
    private String mailFrom;

    /**
     * Alerts an eligible donor that an open request with their blood group needs assistance.
     *
     * @param donor recipient donor user
     * @param request the matching open seeker request
     */
    public void sendMatchNotification(User donor, SeekerRequest request) {
        if (donor == null || donor.getEmail() == null || donor.getEmail().isBlank()) {
            log.warn("Cannot send match notification: Donor email is missing");
            return;
        }

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(mailFrom);
            message.setTo(donor.getEmail());
            message.setSubject("LifeLink Urgent Alert: Blood Match Needed (" + request.getBloodGroup().getLabel() + ")");

            // Note: Under Design Decision #12, no seeker phone or personal contact info is shared in this email!
            String body = String.format(
                "Hello %s,\n\n" +
                "A patient at %s (%s) urgently requires %d unit(s) of %s blood.\n\n" +
                "Details:\n" +
                "- Blood Group: %s\n" +
                "- Urgency: %s\n" +
                "- Hospital: %s, %s\n" +
                "- Required By: %s\n\n" +
                "Please sign in to your LifeLink dashboard to review this request and click 'I Can Help' if available.\n\n" +
                "Personal contact information is kept strictly confidential until you accept the match on LifeLink.\n\n" +
                "Thank you for saving lives,\n" +
                "The LifeLink Platform Team",
                donor.getFullName(),
                request.getHospitalName(),
                request.getSeeker().getCity(),
                request.getUnitsNeeded(),
                request.getBloodGroup().getLabel(),
                request.getBloodGroup().getLabel(),
                request.getUrgency().getLabel(),
                request.getHospitalName(),
                request.getHospitalAddress(),
                request.getRequiredBy()
            );

            message.setText(body);
            mailSender.send(message);
            log.info("Sent match notification email to donor {} for request ID {}", donor.getUsername(), request.getId());
        } catch (Exception ex) {
            // Failure-tolerant: Delivery failures must not disrupt transactional match creation
            log.error("Unable to deliver match notification to donor email {} (Request ID: {}): {}",
                    donor.getEmail(), request.getId(), ex.getMessage());
        }
    }
}
