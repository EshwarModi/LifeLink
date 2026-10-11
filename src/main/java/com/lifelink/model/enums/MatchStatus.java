package com.lifelink.model.enums;

import com.lifelink.service.exception.InvalidRequestStateException;
import lombok.Getter;

/**
 * Status of a donor match to a seeker request, enforcing the canonical match state machine.
 *
 * <p>Allowed transitions:
 * <ul>
 *   <li>{@code (null / no match row) -> ACCEPTED} (direct "I Can Help" upsert)</li>
 *   <li>{@code PENDING -> ACCEPTED}, {@code PENDING -> DECLINED}</li>
 *   <li>{@code DECLINED -> ACCEPTED} (donor changed their mind)</li>
 *   <li>{@code ACCEPTED -> ACCEPTED} (idempotent no-op)</li>
 *   <li>{@code DECLINED -> DECLINED} (idempotent no-op)</li>
 * </ul>
 * All other transitions (including {@code EXPIRED -> anything} and {@code ACCEPTED -> DECLINED})
 * are rejected with {@link InvalidRequestStateException}.</p>
 */
@Getter
public enum MatchStatus {
    PENDING("Pending"),
    ACCEPTED("Accepted"),
    DECLINED("Declined"),
    EXPIRED("Expired");

    private final String label;

    MatchStatus(String label) {
        this.label = label;
    }

    /**
     * Returns {@code true} if transitioning from this status to {@code target} is permitted
     * (either as an active state change or an idempotent no-op).
     */
    public boolean canTransitionTo(MatchStatus target) {
        if (target == null) {
            return false;
        }
        return switch (this) {
            case PENDING -> target == ACCEPTED || target == DECLINED;
            case DECLINED -> target == ACCEPTED || target == DECLINED;
            case ACCEPTED -> target == ACCEPTED;
            case EXPIRED -> false;
        };
    }

    /**
     * Returns {@code true} if transitioning from this status to {@code target} is a permitted
     * idempotent no-op ({@code ACCEPTED -> ACCEPTED} or {@code DECLINED -> DECLINED}).
     */
    public boolean isIdempotentNoOp(MatchStatus target) {
        return (this == ACCEPTED && target == ACCEPTED)
                || (this == DECLINED && target == DECLINED);
    }

    /**
     * Validates a state transition from {@code current} (which may be {@code null} when no
     * match row exists yet) to {@code target}, throwing {@link InvalidRequestStateException}
     * if the transition is not permitted.
     */
    public static void validateTransition(MatchStatus current, MatchStatus target) {
        if (target == null) {
            throw new InvalidRequestStateException("Target match status is required.");
        }
        if (current == null) {
            if (target == ACCEPTED) {
                return;
            }
            throw new InvalidRequestStateException("Cannot transition from no match record to " + target + ".");
        }
        if (!current.canTransitionTo(target)) {
            if (current == EXPIRED) {
                throw new InvalidRequestStateException(
                        "This match has expired and can no longer be " + target.name().toLowerCase() + ".");
            }
            if (current == ACCEPTED && target == DECLINED) {
                throw new InvalidRequestStateException(
                        "Cannot decline a match after accepting it because contact details have already been shared.");
            }
            throw new InvalidRequestStateException(
                    "Invalid match status transition from " + current + " to " + target + ".");
        }
    }
}
