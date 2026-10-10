package com.lifelink.security;

import com.lifelink.model.User;
import com.lifelink.model.enums.UserType;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Custom implementation of Spring Security's {@link UserDetails}.
 *
 * <p>Wraps the domain {@link User} entity and exposes role authorities:
 * <ul>
 *   <li>{@code ROLE_DONOR} for {@link UserType#DONOR}</li>
 *   <li>{@code ROLE_SEEKER} for {@link UserType#SEEKER}</li>
 *   <li>{@code ROLE_ADMIN} for {@link UserType#ADMIN} or when {@code isAdmin == true}</li>
 * </ul>
 * </p>
 */
@Getter
public class CustomUserDetails implements UserDetails {

    private final Long id;
    private final String username;
    private final String email;
    private final String password;
    private final String fullName;
    private final String phone;
    private final String city;
    private final String state;
    private final UserType userType;
    private final boolean admin;
    private final Collection<? extends GrantedAuthority> authorities;

    public CustomUserDetails(User user) {
        this.id = user.getId();
        this.username = user.getUsername();
        this.email = user.getEmail();
        this.password = user.getPasswordHash();
        this.fullName = user.getFullName();
        this.phone = user.getPhone();
        this.city = user.getCity();
        this.state = user.getState();
        this.userType = user.getUserType();
        this.admin = user.isAdmin();

        List<SimpleGrantedAuthority> authList = new ArrayList<>();
        // Assign primary role based on UserType
        if (user.getUserType() != null) {
            authList.add(new SimpleGrantedAuthority("ROLE_" + user.getUserType().name()));
        }
        // If user has admin flag set, also grant ROLE_ADMIN
        if (user.isAdmin() && !authList.contains(new SimpleGrantedAuthority("ROLE_ADMIN"))) {
            authList.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
        }
        this.authorities = authList;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
