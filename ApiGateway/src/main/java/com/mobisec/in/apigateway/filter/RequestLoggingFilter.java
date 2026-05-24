package com.mobisec.in.apigateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Global filter for structured access logging at the API Gateway level.
 *
 * <p>Logs the following fields for every request:
 * <ul>
 *   <li>Correlation ID (from {@link CorrelationIdFilter})</li>
 *   <li>HTTP method</li>
 *   <li>Request path</li>
 *   <li>HTTP response status code</li>
 *   <li>Total execution time in milliseconds</li>
 * </ul>
 *
 * <p>This filter runs at {@link Ordered#HIGHEST_PRECEDENCE} + 1 so it runs
 * immediately after the {@link CorrelationIdFilter}, ensuring the correlation ID
 * is already in the MDC before we start logging.
 */
@Slf4j
@Component
public class RequestLoggingFilter implements GlobalFilter, Ordered {

    @Override
    public int getOrder() {
        // Run just after CorrelationIdFilter (HIGHEST_PRECEDENCE = Integer.MIN_VALUE)
        return Ordered.HIGHEST_PRECEDENCE + 1;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        long startTime = System.currentTimeMillis();
        ServerHttpRequest request = exchange.getRequest();

        String correlationId = request.getHeaders()
                .getFirst(CorrelationIdFilter.CORRELATION_ID_HEADER);
        HttpMethod method = request.getMethod();
        String path = request.getURI().getPath();

        log.info("[{}] --> {} {}", correlationId, method, path);

        return chain.filter(exchange)
                .doFinally(signalType -> {
                    long duration = System.currentTimeMillis() - startTime;
                    HttpStatus status = (HttpStatus) exchange.getResponse().getStatusCode();
                    int statusCode = status != null ? status.value() : 0;

                    // Log at WARN for 4xx/5xx to make errors easy to spot
                    if (statusCode >= 400) {
                        log.warn("[{}] <-- {} {} | status={} | duration={}ms",
                                correlationId, method, path, statusCode, duration);
                    } else {
                        log.info("[{}] <-- {} {} | status={} | duration={}ms",
                                correlationId, method, path, statusCode, duration);
                    }
                });
    }
}
