package com.mobisec.in.apigateway.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

/**
 * Global exception handler for the reactive API Gateway.
 *
 * <p>Intercepts unhandled exceptions that escape the normal filter/handler chain
 * and converts them into structured JSON error responses.
 *
 * <p>Handles:
 * <ul>
 *   <li>{@link InvalidBearerTokenException} — malformed or expired JWTs → 401</li>
 *   <li>{@link ResponseStatusException} — Spring's own status exceptions → mapped directly</li>
 *   <li>All other {@link Throwable}s — generic server error → 500</li>
 * </ul>
 *
 * <p>This handler is ordered at -2 to run before Spring Boot's default error handler
 * but after Spring Security's exception handling.
 */
@Slf4j
@Order(-2)
@Configuration
public class GlobalErrorWebExceptionHandler implements ErrorWebExceptionHandler {

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        HttpStatus status;
        String message;

        if (ex instanceof InvalidBearerTokenException) {
            status = HttpStatus.UNAUTHORIZED;
            message = "Invalid or expired access token.";
            log.warn("JWT validation failed: {}", ex.getMessage());

        } else if (ex instanceof ResponseStatusException rse) {
            status = HttpStatus.valueOf(rse.getStatusCode().value());
            message = rse.getReason() != null ? rse.getReason() : status.getReasonPhrase();

        } else {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
            message = "An unexpected error occurred. Please try again.";
            log.error("Unhandled gateway exception: {}", ex.getMessage(), ex);
        }

        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);

        String errorBody = buildErrorJson(status, message, exchange.getRequest().getPath().value());
        DataBuffer buffer = exchange.getResponse()
                .bufferFactory()
                .wrap(errorBody.getBytes(StandardCharsets.UTF_8));

        return exchange.getResponse().writeWith(Mono.just(buffer));
    }

    /**
     * Builds a structured JSON error response body.
     */
    private String buildErrorJson(HttpStatus status, String message, String path) {
        return String.format(
                "{\"timestamp\":\"%s\",\"status\":%d,\"error\":\"%s\",\"message\":\"%s\",\"path\":\"%s\"}",
                Instant.now().toString(),
                status.value(),
                status.getReasonPhrase(),
                escapeJson(message),
                escapeJson(path)
        );
    }

    /**
     * Minimal JSON string escaping to prevent injection in error messages.
     */
    private String escapeJson(String value) {
        if (value == null) return "";
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }
}
