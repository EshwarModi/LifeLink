package com.lifelink.service;

import com.lifelink.model.enums.BloodGroup;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests validating red blood cell (packed RBC) ABO and Rh factor compatibility matrix.
 */
class BloodCompatibilityTest {

    // =========================================================================
    // Donor -> Recipient Compatibility Tests (All 8 Groups)
    // =========================================================================

    @ParameterizedTest
    @EnumSource(BloodGroup.class)
    @DisplayName("O- is Universal Red Cell Donor: Can donate to all 8 blood groups")
    void testONegativeDonatesToAll(BloodGroup recipient) {
        assertThat(BloodCompatibility.isCompatible(BloodGroup.O_NEGATIVE, recipient)).isTrue();
    }

    @Test
    @DisplayName("O+ can donate to O+, A+, B+, AB+ (all Rh-positive groups) and rejects all Rh-negative")
    void testOPositiveCompatibility() {
        Set<BloodGroup> recipients = BloodCompatibility.compatibleRecipientsFor(BloodGroup.O_POSITIVE);
        assertThat(recipients).containsExactlyInAnyOrder(
                BloodGroup.O_POSITIVE, BloodGroup.A_POSITIVE, BloodGroup.B_POSITIVE, BloodGroup.AB_POSITIVE
        );

        assertThat(BloodCompatibility.isCompatible(BloodGroup.O_POSITIVE, BloodGroup.O_NEGATIVE)).isFalse();
        assertThat(BloodCompatibility.isCompatible(BloodGroup.O_POSITIVE, BloodGroup.A_NEGATIVE)).isFalse();
        assertThat(BloodCompatibility.isCompatible(BloodGroup.O_POSITIVE, BloodGroup.B_NEGATIVE)).isFalse();
        assertThat(BloodCompatibility.isCompatible(BloodGroup.O_POSITIVE, BloodGroup.AB_NEGATIVE)).isFalse();
    }

    @Test
    @DisplayName("A- can donate to A-, A+, AB-, AB+")
    void testANegativeCompatibility() {
        Set<BloodGroup> recipients = BloodCompatibility.compatibleRecipientsFor(BloodGroup.A_NEGATIVE);
        assertThat(recipients).containsExactlyInAnyOrder(
                BloodGroup.A_NEGATIVE, BloodGroup.A_POSITIVE, BloodGroup.AB_NEGATIVE, BloodGroup.AB_POSITIVE
        );
        assertThat(BloodCompatibility.isCompatible(BloodGroup.A_NEGATIVE, BloodGroup.B_POSITIVE)).isFalse();
        assertThat(BloodCompatibility.isCompatible(BloodGroup.A_NEGATIVE, BloodGroup.O_POSITIVE)).isFalse();
    }

    @Test
    @DisplayName("A+ can donate to A+ and AB+")
    void testAPositiveCompatibility() {
        Set<BloodGroup> recipients = BloodCompatibility.compatibleRecipientsFor(BloodGroup.A_POSITIVE);
        assertThat(recipients).containsExactlyInAnyOrder(
                BloodGroup.A_POSITIVE, BloodGroup.AB_POSITIVE
        );
        assertThat(BloodCompatibility.isCompatible(BloodGroup.A_POSITIVE, BloodGroup.A_NEGATIVE)).isFalse();
        assertThat(BloodCompatibility.isCompatible(BloodGroup.A_POSITIVE, BloodGroup.B_POSITIVE)).isFalse();
    }

    @Test
    @DisplayName("B- can donate to B-, B+, AB-, AB+")
    void testBNegativeCompatibility() {
        Set<BloodGroup> recipients = BloodCompatibility.compatibleRecipientsFor(BloodGroup.B_NEGATIVE);
        assertThat(recipients).containsExactlyInAnyOrder(
                BloodGroup.B_NEGATIVE, BloodGroup.B_POSITIVE, BloodGroup.AB_NEGATIVE, BloodGroup.AB_POSITIVE
        );
        assertThat(BloodCompatibility.isCompatible(BloodGroup.B_NEGATIVE, BloodGroup.A_POSITIVE)).isFalse();
    }

    @Test
    @DisplayName("B+ can donate to B+ and AB+")
    void testBPositiveCompatibility() {
        Set<BloodGroup> recipients = BloodCompatibility.compatibleRecipientsFor(BloodGroup.B_POSITIVE);
        assertThat(recipients).containsExactlyInAnyOrder(
                BloodGroup.B_POSITIVE, BloodGroup.AB_POSITIVE
        );
        assertThat(BloodCompatibility.isCompatible(BloodGroup.B_POSITIVE, BloodGroup.B_NEGATIVE)).isFalse();
    }

    @Test
    @DisplayName("AB- can donate to AB- and AB+")
    void testABNegativeCompatibility() {
        Set<BloodGroup> recipients = BloodCompatibility.compatibleRecipientsFor(BloodGroup.AB_NEGATIVE);
        assertThat(recipients).containsExactlyInAnyOrder(
                BloodGroup.AB_NEGATIVE, BloodGroup.AB_POSITIVE
        );
        assertThat(BloodCompatibility.isCompatible(BloodGroup.AB_NEGATIVE, BloodGroup.A_NEGATIVE)).isFalse();
    }

    @Test
    @DisplayName("AB+ can donate ONLY to AB+ and rejects all other 7 groups")
    void testABPositiveDonatesOnlyToABPositive() {
        Set<BloodGroup> recipients = BloodCompatibility.compatibleRecipientsFor(BloodGroup.AB_POSITIVE);
        assertThat(recipients).containsExactly(BloodGroup.AB_POSITIVE);

        for (BloodGroup bg : BloodGroup.values()) {
            if (bg != BloodGroup.AB_POSITIVE) {
                assertThat(BloodCompatibility.isCompatible(BloodGroup.AB_POSITIVE, bg))
                        .as("AB+ should not be compatible with %s", bg)
                        .isFalse();
            }
        }
    }

    // =========================================================================
    // compatibleDonorsFor(requested) Tests
    // =========================================================================

    @Test
    @DisplayName("compatibleDonorsFor(O-) returns only O-")
    void testCompatibleDonorsForONegative() {
        List<BloodGroup> donors = BloodCompatibility.compatibleDonorsFor(BloodGroup.O_NEGATIVE);
        assertThat(donors).containsExactly(BloodGroup.O_NEGATIVE);
    }

    @Test
    @DisplayName("compatibleDonorsFor(O+) returns [O+, O-]")
    void testCompatibleDonorsForOPositive() {
        List<BloodGroup> donors = BloodCompatibility.compatibleDonorsFor(BloodGroup.O_POSITIVE);
        assertThat(donors).containsExactlyInAnyOrder(BloodGroup.O_POSITIVE, BloodGroup.O_NEGATIVE);
        assertThat(donors.get(0)).isEqualTo(BloodGroup.O_POSITIVE); // exact match is ranked first
    }

    @Test
    @DisplayName("compatibleDonorsFor(A-) returns [A-, O-]")
    void testCompatibleDonorsForANegative() {
        List<BloodGroup> donors = BloodCompatibility.compatibleDonorsFor(BloodGroup.A_NEGATIVE);
        assertThat(donors).containsExactlyInAnyOrder(BloodGroup.A_NEGATIVE, BloodGroup.O_NEGATIVE);
        assertThat(donors.get(0)).isEqualTo(BloodGroup.A_NEGATIVE);
    }

    @Test
    @DisplayName("compatibleDonorsFor(A+) returns [A+, A-, O+, O-]")
    void testCompatibleDonorsForAPositive() {
        List<BloodGroup> donors = BloodCompatibility.compatibleDonorsFor(BloodGroup.A_POSITIVE);
        assertThat(donors).containsExactlyInAnyOrder(
                BloodGroup.A_POSITIVE, BloodGroup.A_NEGATIVE, BloodGroup.O_POSITIVE, BloodGroup.O_NEGATIVE
        );
        assertThat(donors.get(0)).isEqualTo(BloodGroup.A_POSITIVE);
    }

    @Test
    @DisplayName("compatibleDonorsFor(B-) returns [B-, O-]")
    void testCompatibleDonorsForBNegative() {
        List<BloodGroup> donors = BloodCompatibility.compatibleDonorsFor(BloodGroup.B_NEGATIVE);
        assertThat(donors).containsExactlyInAnyOrder(BloodGroup.B_NEGATIVE, BloodGroup.O_NEGATIVE);
        assertThat(donors.get(0)).isEqualTo(BloodGroup.B_NEGATIVE);
    }

    @Test
    @DisplayName("compatibleDonorsFor(B+) returns [B+, B-, O+, O-]")
    void testCompatibleDonorsForBPositive() {
        List<BloodGroup> donors = BloodCompatibility.compatibleDonorsFor(BloodGroup.B_POSITIVE);
        assertThat(donors).containsExactlyInAnyOrder(
                BloodGroup.B_POSITIVE, BloodGroup.B_NEGATIVE, BloodGroup.O_POSITIVE, BloodGroup.O_NEGATIVE
        );
        assertThat(donors.get(0)).isEqualTo(BloodGroup.B_POSITIVE);
    }

    @Test
    @DisplayName("compatibleDonorsFor(AB-) returns [AB-, A-, B-, O-]")
    void testCompatibleDonorsForABNegative() {
        List<BloodGroup> donors = BloodCompatibility.compatibleDonorsFor(BloodGroup.AB_NEGATIVE);
        assertThat(donors).containsExactlyInAnyOrder(
                BloodGroup.AB_NEGATIVE, BloodGroup.A_NEGATIVE, BloodGroup.B_NEGATIVE, BloodGroup.O_NEGATIVE
        );
        assertThat(donors.get(0)).isEqualTo(BloodGroup.AB_NEGATIVE);
    }

    @Test
    @DisplayName("compatibleDonorsFor(AB+) returns all 8 blood groups (Universal Recipient)")
    void testCompatibleDonorsForABPositive() {
        List<BloodGroup> donors = BloodCompatibility.compatibleDonorsFor(BloodGroup.AB_POSITIVE);
        assertThat(donors).hasSize(8);
        assertThat(donors.get(0)).isEqualTo(BloodGroup.AB_POSITIVE);
    }

    @Test
    @DisplayName("Null safety: null donor/recipient returns false or empty list")
    void testNullSafety() {
        assertThat(BloodCompatibility.isCompatible(null, BloodGroup.O_POSITIVE)).isFalse();
        assertThat(BloodCompatibility.isCompatible(BloodGroup.O_POSITIVE, null)).isFalse();
        assertThat(BloodCompatibility.compatibleRecipientsFor(null)).isEmpty();
        assertThat(BloodCompatibility.compatibleDonorsFor(null)).isEmpty();
    }
}
