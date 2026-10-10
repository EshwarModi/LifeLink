package com.lifelink;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Entry point for the LifeLink Blood Donation Platform.
 *
 * <p>Key Configuration Annotations:
 * <ul>
 *   <li>{@link SpringBootApplication}: Combines {@code @Configuration},
 *       {@code @EnableAutoConfiguration}, and {@code @ComponentScan}.</li>
 *   <li>{@link EnableScheduling}: Activates Spring's background task scheduling
 *       used to regularly expire overdue seeker requests.</li>
 * </ul>
 * </p>
 */
@SpringBootApplication
@EnableScheduling
public class LifeLinkApplication {

    public static void main(String[] args) {
        SpringApplication.run(LifeLinkApplication.class, args);
    }
}
