package com.lifelink.config;

/**
 * Matching mode configuration for the LifeLink matching engine.
 *
 * <ul>
 *   <li>{@link #EXACT}: Exact blood group equality (default behavior, e.g. O+ to O+).</li>
 *   <li>{@link #COMPATIBLE}: ABO and Rh red blood cell compatibility (e.g. O- universal red cell donor).</li>
 * </ul>
 */
public enum MatchingMode {
    EXACT,
    COMPATIBLE
}
