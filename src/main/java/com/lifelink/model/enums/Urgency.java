package com.lifelink.model.enums;

import lombok.Getter;

/**
 * Urgency levels for a blood request.
 */
@Getter
public enum Urgency {
    LOW("Low"),
    MEDIUM("Medium"),
    HIGH("High"),
    CRITICAL("Critical");

    private final String label;

    Urgency(String label) {
        this.label = label;
    }
}
