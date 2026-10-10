package com.lifelink.model.enums;

import lombok.Getter;

/**
 * Lifecycle states of a blood seeker request.
 */
@Getter
public enum RequestStatus {
    OPEN("Open"),
    FULFILLED("Fulfilled"),
    CANCELLED("Cancelled"),
    EXPIRED("Expired");

    private final String label;

    RequestStatus(String label) {
        this.label = label;
    }
}
