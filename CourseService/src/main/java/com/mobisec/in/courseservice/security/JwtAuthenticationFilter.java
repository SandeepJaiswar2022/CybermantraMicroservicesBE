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

        // Fully public — skip filter entirely, no token processing at all
        if (isFullyPublicEndpoint(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            String token = extractTokenFromRequest(request);

            if (token == null) {
                if (isOptionalAuthEndpoint(request)) {
                    // No token on an optional-auth endpoint — treat as public user
                    // userId and userRole attributes remain null
                    // Service layer handles null userId as "public/unauthenticated"
                    log.debug("No token on optional-auth endpoint — proceeding as public");
                    filterChain.doFilter(request, response);
                    return;
                }

                // No token on a protected endpoint — reject immediately
                sendUnauthorizedResponse(response, "Authorization header is missing");
                return;
            }

            // Token is present — validate fully regardless of endpoint type
            // If token is present but invalid, always reject — even on optional-auth endpoints
            // A user sending a bad token is not the same as a user sending no token
            jwtTokenProvider.validateToken(token);

            String tokenType = jwtTokenProvider.extractTokenType(token);
            String role = jwtTokenProvider.extractRole(token);

            UsernamePasswordAuthenticationToken authentication;

            if ("SERVICE".equals(tokenType)) {
                String serviceName = jwtTokenProvider.extractSubject(token);
                log.debug("Service token - service: {}", serviceName);

                authentication = new UsernamePasswordAuthenticationToken(
                        serviceName,
                        null,
                        Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + role)));

                request.setAttribute("callerServiceName", serviceName);

            } else if ("ACCESS".equals(tokenType)) {
                UUID userId = jwtTokenProvider.extractUserId(token);
                log.debug("User token - userId: {}, role: {}", userId, role);

                authentication = new UsernamePasswordAuthenticationToken(
                        userId,
                        null,
                        Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + role)));

                request.setAttribute("userId", userId);
                request.setAttribute("userRole", role);

            } else {
                log.error("Unknown tokenType: {}", tokenType);
                sendUnauthorizedResponse(response, "Invalid token type");
                return;
            }

            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);

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
     * Fully public endpoints — filter skipped entirely.
     * No token processing whatsoever.
     */
    private boolean isFullyPublicEndpoint(HttpServletRequest request) {
        String path = request.getRequestURI();
        String method = request.getMethod();

        return path.equals("/health") ||
                path.startsWith("/actuator/health") ||
                // Categories GET is fully public — no role-based branching needed
                (path.startsWith("/api/v1/categories") && method.equals("GET"));
    }

    /**
     * Optional-auth endpoints — token is processed if present, ignored if absent.
     * Service layer must handle null userId gracefully.
     */
    private boolean isOptionalAuthEndpoint(HttpServletRequest request) {
        String path = request.getRequestURI();
        String method = request.getMethod();

        return path.equals("/api/v1/courses") && method.equals("GET");
    }

    private String extractTokenFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }

    private void sendUnauthorizedResponse(HttpServletResponse response, String message)
            throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.getWriter().write(String.format(
                "{\"success\":false,\"message\":\"%s\",\"timestamp\":\"%s\"}",
                message,
                java.time.LocalDateTime.now()));
    }

    // shouldNotFilter is now intentionally always false
    // We handle all skip logic internally with isFullyPublicEndpoint
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return false;
    }
}

