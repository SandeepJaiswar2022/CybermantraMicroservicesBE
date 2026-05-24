package com.mobisec.in.apigateway.filter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link CorrelationIdFilter}.
 *
 * <p>Verifies that:
 * <ul>
 *   <li>Existing correlation IDs from the client are preserved</li>
 *   <li>Missing correlation IDs are auto-generated as UUIDs</li>
 *   <li>The correlation ID is added to the response header</li>
 *   <li>The correlation ID is added to the downstream request header</li>
 *   <li>Filter order is correct (HIGHEST_PRECEDENCE)</li>
 * </ul>
 */
class CorrelationIdFilterTest {

    private CorrelationIdFilter correlationIdFilter;

    @BeforeEach
    void setUp() {
        correlationIdFilter = new CorrelationIdFilter();
    }

    @Test
    @DisplayName("Should preserve existing X-Request-ID from the client")
    void filter_withExistingCorrelationId_shouldPreserveIt() {
        String existingId = "test-correlation-id-12345";

        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/v1/courses")
                .header(CorrelationIdFilter.CORRELATION_ID_HEADER, existingId)
                .build();

        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        AtomicReference<ServerWebExchange> capturedExchange = new AtomicReference<>();

        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(chain.filter(any())).thenAnswer(invocation -> {
            capturedExchange.set(invocation.getArgument(0));
            return Mono.empty();
        });

        StepVerifier.create(correlationIdFilter.filter(exchange, chain))
                .verifyComplete();

        // Verify the existing ID was forwarded downstream
        String downstreamId = capturedExchange.get()
                .getRequest()
                .getHeaders()
                .getFirst(CorrelationIdFilter.CORRELATION_ID_HEADER);

        assertThat(downstreamId).isEqualTo(existingId);

        // Verify the same ID was echoed in the response
        String responseId = exchange.getResponse()
                .getHeaders()
                .getFirst(CorrelationIdFilter.CORRELATION_ID_HEADER);

        assertThat(responseId).isEqualTo(existingId);
    }

    @Test
    @DisplayName("Should generate a UUID when no X-Request-ID is present")
    void filter_withoutCorrelationId_shouldGenerateUuid() {
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/v1/users/me")
                .build();

        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        AtomicReference<ServerWebExchange> capturedExchange = new AtomicReference<>();

        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(chain.filter(any())).thenAnswer(invocation -> {
            capturedExchange.set(invocation.getArgument(0));
            return Mono.empty();
        });

        StepVerifier.create(correlationIdFilter.filter(exchange, chain))
                .verifyComplete();

        String generatedId = capturedExchange.get()
                .getRequest()
                .getHeaders()
                .getFirst(CorrelationIdFilter.CORRELATION_ID_HEADER);

        // Must not be null and must look like a UUID
        assertThat(generatedId).isNotNull();
        assertThat(generatedId).matches(
                "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"
        );
    }

    @Test
    @DisplayName("Should add correlation ID to response headers")
    void filter_shouldAddCorrelationIdToResponse() {
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/v1/content/456")
                .build();

        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());

        StepVerifier.create(correlationIdFilter.filter(exchange, chain))
                .verifyComplete();

        String responseCorrelationId = exchange.getResponse()
                .getHeaders()
                .getFirst(CorrelationIdFilter.CORRELATION_ID_HEADER);

        assertThat(responseCorrelationId).isNotNull().isNotBlank();
    }

    @Test
    @DisplayName("Filter order should be HIGHEST_PRECEDENCE")
    void filter_orderShouldBeHighestPrecedence() {
        assertThat(correlationIdFilter.getOrder())
                .isEqualTo(org.springframework.core.Ordered.HIGHEST_PRECEDENCE);
    }

    @Test
    @DisplayName("Each request without correlation ID should get a unique UUID")
    void filter_multipleRequests_shouldGetUniqueIds() {
        AtomicReference<String> firstId = new AtomicReference<>();
        AtomicReference<String> secondId = new AtomicReference<>();

        // First request
        MockServerWebExchange exchange1 = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/courses").build());
        GatewayFilterChain chain1 = mock(GatewayFilterChain.class);
        when(chain1.filter(any())).thenAnswer(inv -> {
            firstId.set(((ServerWebExchange) inv.getArgument(0))
                    .getRequest().getHeaders()
                    .getFirst(CorrelationIdFilter.CORRELATION_ID_HEADER));
            return Mono.empty();
        });
        correlationIdFilter.filter(exchange1, chain1).block();

        // Second request
        MockServerWebExchange exchange2 = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/courses").build());
        GatewayFilterChain chain2 = mock(GatewayFilterChain.class);
        when(chain2.filter(any())).thenAnswer(inv -> {
            secondId.set(((ServerWebExchange) inv.getArgument(0))
                    .getRequest().getHeaders()
                    .getFirst(CorrelationIdFilter.CORRELATION_ID_HEADER));
            return Mono.empty();
        });
        correlationIdFilter.filter(exchange2, chain2).block();

        assertThat(firstId.get()).isNotEqualTo(secondId.get());
    }
}
