package com.lifelink.service;

import com.lifelink.model.enums.BloodGroup;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Utility managing red blood cell (packed RBC) ABO and Rh factor compatibility rules.
 *
 * <p><h3>Red Blood Cell Donor -> Recipient Compatibility Matrix:</h3>
 * <ul>
 *   <li><b>O-</b> can donate to: all 8 groups (Universal Red Cell Donor)</li>
 *   <li><b>O+</b> can donate to: O+, A+, B+, AB+</li>
 *   <li><b>A-</b> can donate to: A-, A+, AB-, AB+</li>
 *   <li><b>A+</b> can donate to: A+, AB+</li>
 *   <li><b>B-</b> can donate to: B-, B+, AB-, AB+</li>
 *   <li><b>B+</b> can donate to: B+, AB+</li>
 *   <li><b>AB-</b> can donate to: AB-, AB+</li>
 *   <li><b>AB+</b> can donate to: AB+ (Universal Red Cell Recipient)</li>
 * </ul>
 * </p>
 */
public final class BloodCompatibility {

    private static final Map<BloodGroup, Set<BloodGroup>> DONOR_TO_RECIPIENTS;

    static {
        Map<BloodGroup, Set<BloodGroup>> map = new EnumMap<>(BloodGroup.class);

        // O- can donate to all 8 groups
        map.put(BloodGroup.O_NEGATIVE, EnumSet.allOf(BloodGroup.class));

        // O+ can donate to: O+, A+, B+, AB+
        map.put(BloodGroup.O_POSITIVE, EnumSet.of(
                BloodGroup.O_POSITIVE, BloodGroup.A_POSITIVE, BloodGroup.B_POSITIVE, BloodGroup.AB_POSITIVE
        ));

        // A- can donate to: A-, A+, AB-, AB+
        map.put(BloodGroup.A_NEGATIVE, EnumSet.of(
                BloodGroup.A_NEGATIVE, BloodGroup.A_POSITIVE, BloodGroup.AB_NEGATIVE, BloodGroup.AB_POSITIVE
        ));

        // A+ can donate to: A+, AB+
        map.put(BloodGroup.A_POSITIVE, EnumSet.of(
                BloodGroup.A_POSITIVE, BloodGroup.AB_POSITIVE
        ));

        // B- can donate to: B-, B+, AB-, AB+
        map.put(BloodGroup.B_NEGATIVE, EnumSet.of(
                BloodGroup.B_NEGATIVE, BloodGroup.B_POSITIVE, BloodGroup.AB_NEGATIVE, BloodGroup.AB_POSITIVE
        ));

        // B+ can donate to: B+, AB+
        map.put(BloodGroup.B_POSITIVE, EnumSet.of(
                BloodGroup.B_POSITIVE, BloodGroup.AB_POSITIVE
        ));

        // AB- can donate to: AB-, AB+
        map.put(BloodGroup.AB_NEGATIVE, EnumSet.of(
                BloodGroup.AB_NEGATIVE, BloodGroup.AB_POSITIVE
        ));

        // AB+ can donate to: AB+ only
        map.put(BloodGroup.AB_POSITIVE, EnumSet.of(
                BloodGroup.AB_POSITIVE
        ));

        DONOR_TO_RECIPIENTS = Collections.unmodifiableMap(map);
    }

    private BloodCompatibility() {
        // Utility class
    }

    /**
     * Checks if a donor's blood group can safely donate red blood cells to a recipient's blood group.
     *
     * @param donor blood group of donor
     * @param recipient blood group of recipient
     * @return true if compatible for red blood cell transfusion
     */
    public static boolean isCompatible(BloodGroup donor, BloodGroup recipient) {
        if (donor == null || recipient == null) {
            return false;
        }
        Set<BloodGroup> recipients = DONOR_TO_RECIPIENTS.get(donor);
        return recipients != null && recipients.contains(recipient);
    }

    /**
     * Returns the set of recipient blood groups that a specific donor can donate red blood cells to.
     *
     * @param donor blood group of donor
     * @return set of recipient blood groups
     */
    public static Set<BloodGroup> compatibleRecipientsFor(BloodGroup donor) {
        if (donor == null) {
            return Set.of();
        }
        return DONOR_TO_RECIPIENTS.getOrDefault(donor, Set.of());
    }

    /**
     * Derives all donor blood groups whose red blood cells are compatible with the requested recipient blood group.
     * Guaranteed to place the exact matching blood group first, followed by compatible non-exact donor groups.
     *
     * @param requested the recipient's requested blood group
     * @return list of compatible donor blood groups with exact match ranked first
     */
    public static List<BloodGroup> compatibleDonorsFor(BloodGroup requested) {
        if (requested == null) {
            return List.of();
        }

        List<BloodGroup> donors = new ArrayList<>();
        // Place exact match first
        donors.add(requested);

        // Append remaining compatible donor groups
        for (Map.Entry<BloodGroup, Set<BloodGroup>> entry : DONOR_TO_RECIPIENTS.entrySet()) {
            BloodGroup candidateDonor = entry.getKey();
            if (candidateDonor != requested && entry.getValue().contains(requested)) {
                donors.add(candidateDonor);
            }
        }

        return Collections.unmodifiableList(donors);
    }
}
