package com.lms.content.security;

import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;
import java.util.UUID;

/**
 * Convenience wrapper for extracting claims from a JWT principal.
 *
 * <p>Centralizing claim extraction avoids duplicate string literals scattered
 * across controllers and service layers. If claim names change, update here only.
 */
public record AuthenticatedUser(Jwt jwt) {

    /** Extract userId from JWT subject claim. */
    public UUID userId() {
        return UUID.fromString(jwt.getSubject());
    }

    /** Check if the user has a given role. */
    public boolean hasRole(String role) {
        List<String> roles = jwt.getClaimAsStringList("roles");
        return roles != null && roles.contains(role);
    }

    public boolean isAdmin() {
        return hasRole("ADMIN");
    }

    public boolean isInstructor() {
        return hasRole("INSTRUCTOR");
    }

    public String email() {
        return jwt.getClaimAsString("email");
    }
}
