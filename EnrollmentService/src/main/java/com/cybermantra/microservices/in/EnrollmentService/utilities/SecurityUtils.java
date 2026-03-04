package com.cybermantra.microservices.in.EnrollmentService.utilities;


import io.jsonwebtoken.Jwt;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.UUID;

public class SecurityUtils {

    public static UUID getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated()) {
            Object principal = auth.getPrincipal();

            if (principal instanceof String str) {
                return UUID.fromString(str);  // subject is the UUID string
            }
        }
        throw new IllegalStateException("No authenticated user found");
    }
}