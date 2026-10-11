package com.lifelink.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration properties for LifeLink matching engine algorithm,
 * bound from {@code lifelink.matching.*}.
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "lifelink.matching")
public class MatchingProperties {

    /**
     * Matching mode: EXACT (default) or COMPATIBLE.
     */
    private MatchingMode mode = MatchingMode.EXACT;

    /**
     * Minimum match threshold before expanding to wider location tiers or compatible non-exact blood groups (default: 3).
     * Bound from {@code lifelink.matching.min-threshold}.
     */
    private int minThreshold = 3;

    /**
     * Maximum safety cap on the total number of donors matched per request (default: 25).
     * Bound from {@code lifelink.matching.max-matches}.
     */
    private int maxMatches = 25;
}
