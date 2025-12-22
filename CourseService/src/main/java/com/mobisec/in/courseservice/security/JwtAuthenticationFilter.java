package com.mobisec.in.courseservice.security;


import com.mobisec.in.courseservice.exception.JwtAuthenticationException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        // Skip authentication for public endpoints
        if (shouldNotFilter(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            // Extract token from Authorization header
            String token = extractTokenFromRequest(request);

            if (token == null) {
                log.debug("No JWT token found in request");
                sendUnauthorizedResponse(response, "Authorization header is missing");
                return;
            }

            // Validate token
            if (jwtTokenProvider.validateToken(token)) {

                // Extract user information from token
                UUID userId = jwtTokenProvider.extractUserId(token);
                String role = jwtTokenProvider.extractRole(token);

                log.debug("Authenticated user - ID: {}, Role: {}", userId, role);

                // Create authentication object with role-based authority
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(
                                userId,
                                null,
                                Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + role))
                        );

                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                Object details = authentication.getDetails();

                if (details instanceof WebAuthenticationDetails webDetails) {
                    String ip = webDetails.getRemoteAddress();
                    String sessionId = webDetails.getSessionId();

                    log.info("Request IP: {}, Session ID: {}", ip, sessionId);
                }

                // Set authentication in security context
                SecurityContextHolder.getContext().setAuthentication(authentication);

                // Set custom request attributes for easy access in controllers
                request.setAttribute("userId", userId);
                request.setAttribute("userRole", role);

                log.debug("Security context set for user: {}", userId);
            }

        } catch (JwtAuthenticationException e) {
            log.error("JWT authentication failed: {}", e.getMessage());
            sendUnauthorizedResponse(response, e.getMessage());
            return;
        } catch (Exception e) {
            log.error("Unexpected error during authentication", e);
            sendUnauthorizedResponse(response, "Authentication failed");
            return;
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Extract JWT token from Authorization header
     */
    private String extractTokenFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");

        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }

        return null;
    }

    /**
     * Send 401 Unauthorized response
     */
    private void sendUnauthorizedResponse(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.getWriter().write(String.format(
                "{\"success\":false,\"message\":\"%s\",\"timestamp\":\"%s\"}",
                message,
                java.time.LocalDateTime.now()
        ));
    }

    /**
     * Define which endpoints should skip JWT authentication
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        String method = request.getMethod();

        // Public endpoints - no authentication required
        return (path.startsWith("/api/v1/categories") && method.equals("GET")) ||
                path.equals("/health") ||
                path.startsWith("/actuator/health");
    }
}
