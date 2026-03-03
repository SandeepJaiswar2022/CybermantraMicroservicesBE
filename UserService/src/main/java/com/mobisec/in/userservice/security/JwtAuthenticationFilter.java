package com.mobisec.in.userservice.security;

import com.mobisec.in.userservice.exception.JwtAuthenticationException;
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

        if (shouldNotFilter(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            String token = extractTokenFromRequest(request);

            // Fixed: null token on a protected endpoint is an immediate 401
            if (token == null) {
                sendUnauthorizedResponse(response, "Authorization header is missing");
                return;
            }

            jwtTokenProvider.validateToken(token);

            String tokenType = jwtTokenProvider.extractTokenType(token);
            String role = jwtTokenProvider.extractRole(token);

            UsernamePasswordAuthenticationToken authentication;

            if ("SERVICE".equals(tokenType)) {
                // Service token — principal is service name, no userId set
                String serviceName = jwtTokenProvider.extractSubject(token);

                log.debug("Service token authenticated - service: {}, role: {}", serviceName, role);

                authentication = new UsernamePasswordAuthenticationToken(
                        serviceName,
                        null,
                        Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + role)));

                // Explicitly mark this request as a service call — no userId attribute set
                request.setAttribute("callerServiceName", serviceName);

            } else if ("ACCESS".equals(tokenType)) {
                // User token — principal is UUID
                UUID userId = jwtTokenProvider.extractUserId(token);

                log.debug("User token authenticated - userId: {}, role: {}", userId, role);

                authentication = new UsernamePasswordAuthenticationToken(
                        userId,
                        null,
                        Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + role)));

                request.setAttribute("userId", userId);
                request.setAttribute("userRole", role);

            } else {
                log.error("Unknown tokenType claim: {}", tokenType);
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

    private String extractTokenFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }

    private void sendUnauthorizedResponse(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.getWriter().write(String.format(
                "{\"success\":false,\"message\":\"%s\",\"timestamp\":\"%s\"}",
                message,
                java.time.LocalDateTime.now()));
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.equals("/health") ||
                path.startsWith("/actuator/health");
    }
}