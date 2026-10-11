package com.lifelink.notification;

import com.lifelink.model.SeekerRequest;
import com.lifelink.model.User;

import java.time.LocalDate;

/**
 * Immutable domain event published when a new {@link com.lifelink.model.Match} is created.
 *
 * <p>Carries plain value fields only (no JPA entities) because {@code spring.jpa.open-in-view=false}
 * and the asynchronous {@code AFTER_COMMIT} notification listener executes on a background thread
 * without an active Hibernate session.</p>
 */
public record MatchCreatedEvent(
        String donorEmail,
        String donorFullName,
        String donorUsername,
        Long requestId,
        String bloodGroupLabel,
        String urgencyLabel,
        String hospitalName,
        String hospitalAddress,
        String city,
        int units,
        LocalDate requiredBy
) {

    /**
     * Extracts plain event values from the donor and seeker request while still inside the active transaction.
     */
    public static MatchCreatedEvent from(User donor, SeekerRequest request) {
        return new MatchCreatedEvent(
                donor != null ? donor.getEmail() : null,
                donor != null ? donor.getFullName() : null,
                donor != null ? donor.getUsername() : null,
                request != null ? request.getId() : null,
                (request != null && request.getBloodGroup() != null) ? request.getBloodGroup().getLabel() : null,
                (request != null && request.getUrgency() != null) ? request.getUrgency().getLabel() : null,
                request != null ? request.getHospitalName() : null,
                request != null ? request.getHospitalAddress() : null,
                (request != null && request.getSeeker() != null) ? request.getSeeker().getCity() : null,
                (request != null && request.getUnitsNeeded() != null) ? request.getUnitsNeeded() : 0,
                request != null ? request.getRequiredBy() : null
        );
    }
}
