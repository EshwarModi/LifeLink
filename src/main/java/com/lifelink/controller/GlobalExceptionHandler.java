package com.lifelink.controller;

import com.lifelink.service.exception.DuplicateResourceException;
import com.lifelink.service.exception.InvalidRequestStateException;
import com.lifelink.service.exception.ResourceNotFoundException;
import com.lifelink.service.exception.UnauthorizedActionException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Centralized exception handler for both MVC web pages and REST API endpoints.
 */
@Slf4j
@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler({ResourceNotFoundException.class, NoResourceFoundException.class})
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Object handleNotFound(Exception ex, HttpServletRequest request, Model model) {
        log.warn("Resource not found at {}: {}", request.getRequestURI(), ex.getMessage());
        if (isApiRequest(request)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(buildApiError(HttpStatus.NOT_FOUND, ex.getMessage()));
        }
        model.addAttribute("status", 404);
        model.addAttribute("error", "Not Found");
        model.addAttribute("message", ex.getMessage());
        return "error/404";
    }

    @ExceptionHandler({UnauthorizedActionException.class, AccessDeniedException.class})
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public Object handleAccessDenied(Exception ex, HttpServletRequest request, Model model) {
        log.warn("Access denied at {}: {}", request.getRequestURI(), ex.getMessage());
        if (isApiRequest(request)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(buildApiError(HttpStatus.FORBIDDEN, ex.getMessage()));
        }
        model.addAttribute("status", 403);
        model.addAttribute("error", "Forbidden");
        model.addAttribute("message", ex.getMessage());
        return "error/403";
    }

    @ExceptionHandler({InvalidRequestStateException.class, IllegalArgumentException.class, DuplicateResourceException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Object handleBadRequest(Exception ex, HttpServletRequest request, Model model) {
        log.warn("Bad request at {}: {}", request.getRequestURI(), ex.getMessage());
        if (isApiRequest(request)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(buildApiError(HttpStatus.BAD_REQUEST, ex.getMessage()));
        }
        model.addAttribute("status", 400);
        model.addAttribute("error", "Bad Request");
        model.addAttribute("message", ex.getMessage());
        return "error/error";
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Object handleGeneralError(Exception ex, HttpServletRequest request, Model model) {
        log.error("Unhandled error at {}: {}", request.getRequestURI(), ex.getMessage(), ex);
        if (isApiRequest(request)) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(buildApiError(HttpStatus.INTERNAL_SERVER_ERROR, "An internal server error occurred."));
        }
        model.addAttribute("status", 500);
        model.addAttribute("error", "Internal Server Error");
        model.addAttribute("message", "Something went wrong on our end. Please try again later.");
        return "error/500";
    }

    private boolean isApiRequest(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return uri.startsWith("/api/") || uri.startsWith("/actuator/");
    }

    private Map<String, Object> buildApiError(HttpStatus status, String message) {
        return Map.of(
                "timestamp", LocalDateTime.now(),
                "status", status.value(),
                "error", status.getReasonPhrase(),
                "message", message
        );
    }
}
