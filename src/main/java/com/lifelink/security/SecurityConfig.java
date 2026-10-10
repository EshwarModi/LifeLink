package com.lifelink.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Spring Security configuration for the LifeLink platform.
 *
 * <p>Key Architecture & Design Decisions:
 * <ul>
 *   <li><b>CSRF Protection:</b> Always enabled (Design Decision #1).</li>
 *   <li><b>Session Fixation Protection:</b> Enforces session migration on authentication (Design Decision #2).</li>
 *   <li><b>Password Hashing:</b> BCryptPasswordEncoder with strength factor 12 (Design Decision #3).</li>
 *   <li><b>Rate Limiting:</b> Bucket4j filter before UsernamePasswordAuthenticationFilter for POST /login and POST /register (Design Decision #4).</li>
 *   <li><b>Strict Role Separation:</b> /admin/** restricted to ROLE_ADMIN, /seeker/** restricted to ROLE_SEEKER, /donor/** restricted to ROLE_DONOR.</li>
 * </ul>
 * </p>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final CustomUserDetailsService userDetailsService;
    private final CustomAuthenticationSuccessHandler successHandler;
    private final CustomAuthenticationFailureHandler failureHandler;
    private final RateLimitingFilter rateLimitingFilter;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authConfig) throws Exception {
        return authConfig.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // 1. Rate Limiting Filter on POST /login and POST /register
            .addFilterBefore(rateLimitingFilter, UsernamePasswordAuthenticationFilter.class)

            // 2. Authorization Rules
            .authorizeHttpRequests(auth -> auth
                // Public routes: Home, Login, Register, API, Actuator Health, Error, and Static Assets
                .requestMatchers(
                    "/",
                    "/login",
                    "/register",
                    "/api/**",
                    "/actuator/**",
                    "/error",
                    "/css/**",
                    "/js/**",
                    "/images/**",
                    "/webjars/**",
                    "/favicon.ico"
                ).permitAll()

                // Admin-only management endpoints
                .requestMatchers("/admin/**").hasRole("ADMIN")

                // Seeker-specific endpoints
                .requestMatchers(
                    "/seeker/**",
                    "/requests/new",
                    "/requests/*/edit",
                    "/requests/*/cancel",
                    "/requests/*/fulfill"
                ).hasRole("SEEKER")

                // Donor-specific endpoints
                .requestMatchers(
                    "/donor/**",
                    "/matches/*/accept",
                    "/matches/*/decline"
                ).hasRole("DONOR")

                // Shared authenticated endpoints (dashboard, general request viewing, profile)
                .anyRequest().authenticated()
            )

            // 3. Form Login Configuration
            .formLogin(form -> form
                .loginPage("/login")
                .loginProcessingUrl("/login")
                .successHandler(successHandler)
                .failureHandler(failureHandler)
                .permitAll()
            )

            // 4. Logout Configuration
            .logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessUrl("/login?logout=true")
                .invalidateHttpSession(true)
                .deleteCookies("JSESSIONID")
                .permitAll()
            )

            // 5. Session Fixation Protection (Spring default is migrateSession)
            .sessionManagement(session -> session
                .sessionFixation(sf -> sf.migrateSession())
            )

            // 6. UserDetailsService
            .userDetailsService(userDetailsService);

            // CSRF protection remains enabled by default; never disabled.

        return http.build();
    }
}
