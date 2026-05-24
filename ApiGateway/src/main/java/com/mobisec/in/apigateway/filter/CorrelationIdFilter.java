package com.mobisec.in.apigateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Global filter that manages Correlation IDs for end-to-end request tracing.
 *
 * <p>For every inbound request this filter:
 * <ol>
 *   <li>Reads the {@code X-Request-ID} header if provided by the client</li>
 *   <li>Generates a new UUID if no correlation ID is present</li>
 *   <li>Propagates the ID downstream via the request header</li>
 *   <li>Echoes the ID back to the client via the response header</li>
 *   <li>Places the ID in the SLF4J MDC for structured logging</li>
 * </ol>
 *
 * <p>This filter runs at the highest priority ({@link Ordered#HIGHEST_PRECEDENCE})
 * so all downstream filters and route handlers can rely on the ID being present.
 */
@Slf4j
@Component
public class CorrelationIdFilter implements GlobalFilter, Ordered {

    public static final String CORRELATION_ID_HEADER = "X-Request-ID";
    public static final String CORRELATION_ID_MDC_KEY = "correlationId";

    /**
     * Run at the very beginning of the filter chain so all other filters
     * can access the correlation ID from MDC.
     */
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();

        // Read existing correlation ID or generate a new one
        String correlationId = request.getHeaders().getFirst(CORRELATION_ID_HEADER);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
            log.debug("Generated new correlation ID: {}", correlationId);
        } else {
            log.debug("Using existing correlation ID from client: {}", correlationId);
        }

        final String finalCorrelationId = correlationId;

        // Mutate the request to add the correlation ID header for downstream services
        ServerHttpRequest mutatedRequest = request.mutate()
                .header(CORRELATION_ID_HEADER, finalCorrelationId)
                .build();

        // Add the correlation ID to the response so clients can track it
        ServerHttpResponse response = exchange.getResponse();
        response.getHeaders().add(CORRELATION_ID_HEADER, finalCorrelationId);

        // Place the correlation ID in MDC for structured/contextual logging
        MDC.put(CORRELATION_ID_MDC_KEY, finalCorrelationId);

        return chain
                .filter(exchange.mutate().request(mutatedRequest).build())
                .doFinally(signalType -> {
                    // Always clean up MDC to avoid memory leaks in thread pools
                    MDC.remove(CORRELATION_ID_MDC_KEY);
                });
    }
}
