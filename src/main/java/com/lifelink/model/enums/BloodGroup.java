package com.lifelink.model.enums;

import lombok.Getter;

/**
 * Standard ABO and Rh factor blood groups.
 * Deliberate simplification: matching uses exact equality unless specified otherwise.
 */
@Getter
public enum BloodGroup {
    A_POSITIVE("A+"),
    A_NEGATIVE("A-"),
    B_POSITIVE("B+"),
    B_NEGATIVE("B-"),
    AB_POSITIVE("AB+"),
    AB_NEGATIVE("AB-"),
    O_POSITIVE("O+"),
    O_NEGATIVE("O-");

    private final String label;

    BloodGroup(String label) {
        this.label = label;
    }
}
