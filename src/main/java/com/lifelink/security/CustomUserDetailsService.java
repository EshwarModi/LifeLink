package com.lifelink.security;

import com.lifelink.model.User;
import com.lifelink.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Spring Security UserDetailsService implementation.
 * Authenticates users against the database by either username or email address.
 */
@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String identifier) throws UsernameNotFoundException {
        if (identifier == null || identifier.trim().isEmpty()) {
            throw new UsernameNotFoundException("Username or email cannot be empty");
        }

        String trimmedIdentifier = identifier.trim();

        // Support authentication via either username or email
        User user = userRepository.findByUsername(trimmedIdentifier)
                .or(() -> userRepository.findByEmail(trimmedIdentifier))
                .orElseThrow(() -> new UsernameNotFoundException(
                        "No account found with username or email: " + trimmedIdentifier
                ));

        return new CustomUserDetails(user);
    }
}
