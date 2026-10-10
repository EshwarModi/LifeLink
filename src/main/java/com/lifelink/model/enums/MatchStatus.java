package com.lifelink.model.enums;

import lombok.Getter;

/**
 * Status of a donor match to a seeker request.
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
}
