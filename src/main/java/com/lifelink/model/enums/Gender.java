package com.lifelink.model.enums;

import lombok.Getter;

/**
 * Gender categories for donor profiles.
 */
@Getter
public enum Gender {
    MALE("Male"),
    FEMALE("Female"),
    OTHER("Other");

    private final String label;

    Gender(String label) {
        this.label = label;
    }
}
