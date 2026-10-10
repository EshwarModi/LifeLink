package com.lifelink.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Handles post-login redirection based on the authenticated user's role.
 */
@Slf4j
@Component
public class CustomAuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException {

        log.info("[SECURITY_AUDIT] event=LOGIN_SUCCESS username=\"{}\" authorities=\"{}\"",
                authentication.getName(),
                authentication.getAuthorities());

        for (GrantedAuthority authority : authentication.getAuthorities()) {
            String role = authority.getAuthority();
            if ("ROLE_ADMIN".equals(role)) {
                response.sendRedirect(request.getContextPath() + "/admin/dashboard");
                return;
            } else if ("ROLE_DONOR".equals(role)) {
                response.sendRedirect(request.getContextPath() + "/donor/dashboard");
                return;
            } else if ("ROLE_SEEKER".equals(role)) {
                response.sendRedirect(request.getContextPath() + "/seeker/dashboard");
                return;
            }
        }

        response.sendRedirect(request.getContextPath() + "/dashboard");
    }
}
