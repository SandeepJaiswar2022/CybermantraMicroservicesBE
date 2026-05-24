package com.mobisec.in.contentservice.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.UUID;

/**
 * JWT filter — exact same pattern as course-service JwtAuthenticationFilter.
 *
 * Reads the Bearer token, validates it, sets userId / userRole as request
 * attributes so controllers can extract them via httpRequest.getAttribute().
 *
 * All content endpoints require authentication; there are no optional-auth
 * routes in this service.
 */
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

        // ── Fully public — skip entirely ──────────────────────────────────
        if (isFullyPublicEndpoint(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            String token = extractToken(request);

            if (token == null) {
                sendUnauthorized(response, "Authorization header is missing");
                return;
            }

            // Validate — throws JwtAuthenticationException on any failure
            jwtTokenProvider.validateToken(token);

            String tokenType = jwtTokenProvider.extractTokenType(token);
            String role      = jwtTokenProvider.extractRole(token);

            UsernamePasswordAuthenticationToken authentication;

            if ("SERVICE".equals(tokenType)) {
                String serviceName = jwtTokenProvider.extractSubject(token);
                log.debug("Service token — caller: {}", serviceName);

                authentication = new UsernamePasswordAuthenticationToken(
                        serviceName, null,
                        Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + role)));

                request.setAttribute("callerServiceName", serviceName);

            } else if ("ACCESS".equals(tokenType)) {
                UUID userId = jwtTokenProvider.extractUserId(token);
                log.debug("User token — userId: {}, role: {}", userId, role);

                authentication = new UsernamePasswordAuthenticationToken(
                        userId, null,
                        Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + role)));

                // ← same attributes as course-service — controllers use these
                request.setAttribute("userId",   userId);
                request.setAttribute("userRole", role);

            } else {
                log.error("Unknown tokenType: {}", tokenType);
                sendUnauthorized(response, "Invalid token type");
                return;
            }

            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);

        } catch (JwtAuthenticationException e) {
            log.error("JWT authentication failed: {}", e.getMessage());
            sendUnauthorized(response, e.getMessage());
            return;
        } catch (Exception e) {
            log.error("Unexpected error during authentication", e);
            sendUnauthorized(response, "Authentication failed");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean isFullyPublicEndpoint(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.equals("/health") || path.startsWith("/actuator/health");
    }

    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return null;
    }

    private void sendUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.getWriter().write(String.format(
                "{\"success\":false,\"message\":\"%s\",\"timestamp\":\"%s\"}",
                message, java.time.LocalDateTime.now()));
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return false; // handle all logic internally
    }
}
