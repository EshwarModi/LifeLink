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
}
