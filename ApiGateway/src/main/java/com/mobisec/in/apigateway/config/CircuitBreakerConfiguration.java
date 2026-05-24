package com.mobisec.in.apigateway.config;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import org.springframework.cloud.circuitbreaker.resilience4j.ReactiveResilience4JCircuitBreakerFactory;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JConfigBuilder;
import org.springframework.cloud.client.circuitbreaker.Customizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Resilience4j circuit breaker configuration for the API Gateway.
 *
 * <p>Circuit breakers protect the gateway from cascading failures when downstream
 * services (Course Service, Content Service) become slow or unavailable.
 *
 * <p>The circuit breaker state machine:
 * <pre>
 *   CLOSED ──(failure threshold met)──► OPEN
 *     ▲                                    │
 *     │                                    ▼
 *   HALF_OPEN ◄──(wait duration)───── OPEN
 * </pre>
 *
 * <p>When OPEN, requests immediately fail-fast and are redirected to fallback endpoints.
 */
@Configuration
public class CircuitBreakerConfiguration {

    /**
     * Default circuit breaker configuration applied to all circuit breakers unless
     * a more specific configuration is registered.
     *
     * <p>Settings:
     * <ul>
     *   <li>Failure rate threshold: 50% — opens after half of requests fail</li>
     *   <li>Slow call threshold: 80% — opens if 80% of calls exceed 2 seconds</li>
     *   <li>Wait duration in OPEN state: 10 seconds</li>
     *   <li>Minimum calls before evaluation: 5 requests</li>
     *   <li>Permitted calls in HALF-OPEN: 3 (to test recovery)</li>
     * </ul>
     *
     * @return a {@link Customizer} that sets the default Resilience4J config
     */
    @Bean
    public Customizer<ReactiveResilience4JCircuitBreakerFactory> defaultCustomizer() {
        CircuitBreakerConfig circuitBreakerConfig = CircuitBreakerConfig.custom()
                .failureRateThreshold(50)
                .slowCallRateThreshold(80)
                .slowCallDurationThreshold(Duration.ofSeconds(2))
                .waitDurationInOpenState(Duration.ofSeconds(10))
                .minimumNumberOfCalls(5)
                .permittedNumberOfCallsInHalfOpenState(3)
                .slidingWindowSize(10)
                .build();

        // Time limiter: abort calls that exceed 3 seconds
        TimeLimiterConfig timeLimiterConfig = TimeLimiterConfig.custom()
                .timeoutDuration(Duration.ofSeconds(3))
                .build();

        return factory -> factory.configureDefault(id ->
                new Resilience4JConfigBuilder(id)
                        .circuitBreakerConfig(circuitBreakerConfig)
                        .timeLimiterConfig(timeLimiterConfig)
                        .build()
        );
    }

    /**
     * Course Service specific circuit breaker — slightly more tolerant since
     * course listing queries can be heavy.
     *
     * @return customizer for the courseService circuit breaker
     */
    @Bean
    public Customizer<ReactiveResilience4JCircuitBreakerFactory> courseServiceCustomizer() {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .failureRateThreshold(60)
                .slowCallDurationThreshold(Duration.ofSeconds(3))
                .waitDurationInOpenState(Duration.ofSeconds(15))
                .minimumNumberOfCalls(5)
                .build();

        TimeLimiterConfig timeLimiterConfig = TimeLimiterConfig.custom()
                .timeoutDuration(Duration.ofSeconds(5))
                .build();

        return factory -> factory.configure(
                builder -> builder
                        .circuitBreakerConfig(config)
                        .timeLimiterConfig(timeLimiterConfig),
                "courseServiceCircuitBreaker"
        );
    }

    /**
     * Content Service specific circuit breaker — more aggressive because
     * media/upload operations must fail fast to avoid blocking resources.
     *
     * @return customizer for the contentService circuit breaker
     */
    @Bean
    public Customizer<ReactiveResilience4JCircuitBreakerFactory> contentServiceCustomizer() {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .failureRateThreshold(40)
                .slowCallDurationThreshold(Duration.ofSeconds(4))
                .waitDurationInOpenState(Duration.ofSeconds(20))
                .minimumNumberOfCalls(3)
                .build();

        TimeLimiterConfig timeLimiterConfig = TimeLimiterConfig.custom()
                .timeoutDuration(Duration.ofSeconds(10)) // uploads need more time
                .build();

        return factory -> factory.configure(
                builder -> builder
                        .circuitBreakerConfig(config)
                        .timeLimiterConfig(timeLimiterConfig),
                "contentServiceCircuitBreaker"
        );
    }
}
