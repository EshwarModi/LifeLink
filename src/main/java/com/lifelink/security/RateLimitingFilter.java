package com.lifelink.security;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Servlet filter providing IP-based rate limiting on sensitive authentication endpoints:
 * {@code POST /login} and {@code POST /register}.
 *
 * <p>Enforces Design Decision #4: Returns HTTP 429 (Too Many Requests) when the bucket limit is exhausted.</p>
 */
@Component
public class RateLimitingFilter extends OncePerRequestFilter {

    private final Map<String, Bucket> loginBuckets = new ConcurrentHashMap<>();
    private final Map<String, Bucket> registerBuckets = new ConcurrentHashMap<>();

    private static final int LOGIN_CAPACITY = 10;
    private static final int REGISTER_CAPACITY = 5;

    public void resetBuckets() {
        loginBuckets.clear();
        registerBuckets.clear();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String uri = request.getRequestURI();
        String method = request.getMethod();

        if ("POST".equalsIgnoreCase(method)) {
            String clientIp = extractClientIp(request);

            if (uri.endsWith("/login")) {
                Bucket bucket = loginBuckets.computeIfAbsent(clientIp, k -> createBucket(LOGIN_CAPACITY, Duration.ofMinutes(1)));
                if (!bucket.tryConsume(1)) {
                    sendRateLimitResponse(response, "Too many login attempts. Please wait a minute before trying again.");
                    return;
                }
            } else if (uri.endsWith("/register")) {
                Bucket bucket = registerBuckets.computeIfAbsent(clientIp, k -> createBucket(REGISTER_CAPACITY, Duration.ofMinutes(1)));
                if (!bucket.tryConsume(1)) {
                    sendRateLimitResponse(response, "Too many registration attempts. Please wait a minute before trying again.");
                    return;
                }
            }
        }

        filterChain.doFilter(request, response);
    }

    private Bucket createBucket(int capacity, Duration duration) {
        Refill refill = Refill.intervally(capacity, duration);
        Bandwidth limit = Bandwidth.classic(capacity, refill);
        return Bucket.builder().addLimit(limit).build();
    }

    private String extractClientIp(HttpServletRequest request) {
        String xfHeader = request.getHeader("X-Forwarded-For");
        if (xfHeader == null || xfHeader.trim().isEmpty()) {
            return request.getRemoteAddr();
        }
        return xfHeader.split(",")[0].trim();
    }

    private void sendRateLimitResponse(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.TEXT_PLAIN_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(message);
    }
}
