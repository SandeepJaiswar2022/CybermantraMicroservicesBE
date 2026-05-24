package com.mobisec.in.apigateway.fallback;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Map;

/**
 * Fallback controller invoked by Resilience4j circuit breakers when downstream
 * services (Course Service, Content Service) are unavailable or timing out.
 *
 * <p>Returns a structured JSON response that the React frontend can interpret
 * gracefully, preventing cascading failures from propagating to end users.
 *
 * <p>All fallback endpoints are public (no auth required) as defined in
 * {@code SecurityConfig}.
 */
@Slf4j
@RestController
@RequestMapping("/fallback")
public class FallbackController {

    /**
     * Fallback for Course Service circuit breaker.
     * Triggered when Course Service is down or latency exceeds the threshold.
     */
    @GetMapping(value = "/courses", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<Map<String, Object>> courseServiceFallback(ServerWebExchange exchange) {
        log.warn("Course Service circuit breaker triggered — returning fallback response");

        exchange.getResponse().setStatusCode(HttpStatus.SERVICE_UNAVAILABLE);

        return Mono.just(Map.of(
                "timestamp", Instant.now().toString(),
                "status", HttpStatus.SERVICE_UNAVAILABLE.value(),
                "error", "Service Unavailable",
                "message", "Course Service is temporarily unavailable. Please try again shortly.",
                "service", "course-service",
                "path", exchange.getRequest().getPath().value()
        ));
    }

    /**
     * Fallback for Content Service circuit breaker.
     * Triggered when Content Service is down or not responding in time.
     */
    @GetMapping(value = "/content", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<Map<String, Object>> contentServiceFallback(ServerWebExchange exchange) {
        log.warn("Content Service circuit breaker triggered — returning fallback response");

        exchange.getResponse().setStatusCode(HttpStatus.SERVICE_UNAVAILABLE);

        return Mono.just(Map.of(
                "timestamp", Instant.now().toString(),
                "status", HttpStatus.SERVICE_UNAVAILABLE.value(),
                "error", "Service Unavailable",
                "message", "Content Service is temporarily unavailable. Please try again shortly.",
                "service", "content-service",
                "path", exchange.getRequest().getPath().value()
        ));
    }

    /**
     * Generic fallback for any unconfigured service outage.
     */
    @GetMapping(value = "/generic", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<Map<String, Object>> genericFallback(ServerWebExchange exchange) {
        log.warn("Generic circuit breaker fallback triggered");

        exchange.getResponse().setStatusCode(HttpStatus.SERVICE_UNAVAILABLE);

        return Mono.just(Map.of(
                "timestamp", Instant.now().toString(),
                "status", HttpStatus.SERVICE_UNAVAILABLE.value(),
                "error", "Service Unavailable",
                "message", "The requested service is temporarily unavailable.",
                "path", exchange.getRequest().getPath().value()
        ));
    }
}
