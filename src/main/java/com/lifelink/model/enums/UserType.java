package com.lifelink.model.enums;

import lombok.Getter;

/**
 * Functional user roles in LifeLink.
 * DONOR and SEEKER are primary user types; ADMIN is reserved for system management.
 */
@Getter
public enum UserType {
    DONOR("Donor"),
    SEEKER("Seeker"),
    ADMIN("Administrator");

    private final String label;

    UserType(String label) {
        this.label = label;
    }
}
