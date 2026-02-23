package com.cybermantra.microservices.in.EnrollmentService.utilities;


import io.jsonwebtoken.Jwt;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public class SecurityUtils {

    public static Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated()) {
            Object principal = auth.getPrincipal();

            // If your filter sets the userId directly as the principal
            if (principal instanceof Long userId) {
                return userId;
            }

            // If your filter sets it as a String (e.g., subject from JWT)
            if (principal instanceof String str) {
                return Long.parseLong(str);
            }
        }
        throw new IllegalStateException("No authenticated user found");
    }
}