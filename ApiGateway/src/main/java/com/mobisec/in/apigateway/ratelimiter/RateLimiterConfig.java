package com.mobisec.in.apigateway.ratelimiter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.util.Optional;

/**
 * Rate limiter configuration providing a custom {@link KeyResolver} for Redis-based
 * request rate limiting in Spring Cloud Gateway.
 *
 * <p>Key resolution strategy (in priority order):
 * <ol>
 *   <li>Authenticated user's subject claim (user ID) from the JWT</li>
 *   <li>Client IP address from the remote address or X-Forwarded-For header</li>
 *   <li>Fallback key "anonymous" for unresolvable cases</li>
 * </ol>
 *
 * <p>This ensures authenticated users are individually rate-limited while
 * unauthenticated clients are limited per IP address.
 */
@Slf4j
@Configuration
public class RateLimiterConfig {

    /**
     * Primary KeyResolver bean used by the Redis RequestRateLimiter filter.
     *
     * <p>Attempts to extract the user ID from the JWT principal first;
     * falls back to the client IP address if no authentication context is present.
     *
     * @return a reactive KeyResolver
     */
    @Bean
    @Primary
    public KeyResolver userOrIpKeyResolver() {
        return exchange -> ReactiveSecurityContextHolder.getContext()
                .flatMap(securityContext -> {
                    var authentication = securityContext.getAuthentication();
                    if (authentication != null
                            && authentication.isAuthenticated()
                            && authentication.getPrincipal() instanceof Jwt jwt) {

                        // Use the JWT subject (user ID) as the rate-limit key
                        String userId = jwt.getSubject();
                        log.debug("Rate limiter key resolved from JWT subject: {}", userId);
                        return Mono.just("user:" + userId);
                    }
                    return Mono.empty();
                })
                .switchIfEmpty(Mono.defer(() -> {
                    // Fall back to client IP address
                    String ip = resolveClientIp(exchange);
                    log.debug("Rate limiter key resolved from IP: {}", ip);
                    return Mono.just("ip:" + ip);
                }));
    }

    /**
     * Resolves the real client IP address, taking into account proxy headers.
     *
     * <p>Checks X-Forwarded-For first (set by load balancers / reverse proxies),
     * then falls back to the direct remote address.
     *
     * @param exchange the current server web exchange
     * @return the resolved IP address string, or "unknown" if not determinable
     */
    private String resolveClientIp(
            org.springframework.web.server.ServerWebExchange exchange) {

        // Check X-Forwarded-For header (first IP in the chain is the real client)
        String forwardedFor = exchange.getRequest()
                .getHeaders()
                .getFirst("X-Forwarded-For");

        if (forwardedFor != null && !forwardedFor.isBlank()) {
            // Take only the first address (leftmost = original client)
            return forwardedFor.split(",")[0].trim();
        }

        // Fall back to direct remote address
        return Optional.ofNullable(exchange.getRequest().getRemoteAddress())
                .map(InetSocketAddress::getAddress)
                .map(java.net.InetAddress::getHostAddress)
                .orElse("unknown");
    }
}
