package com.lms.content.util;

import org.slf4j.MDC;

/**
 * Utility for correlation ID management in MDC (Mapped Diagnostic Context).
 *
 * <p>The correlation ID is set per-request in {@link com.lms.content.config.CorrelationIdFilter}
 * and automatically included in all log lines via the pattern:
 * {@code [%X{correlationId}]}.
 *
 * <p>This enables distributed tracing across microservices when the same
 * X-Correlation-ID header is propagated through all service calls.
 */
public final class CorrelationIdUtil {

    public static final String CORRELATION_ID_KEY = "correlationId";

    private CorrelationIdUtil() {}

    public static void set(String correlationId) {
        MDC.put(CORRELATION_ID_KEY, correlationId);
    }

    public static String get() {
        return MDC.get(CORRELATION_ID_KEY);
    }

    public static void clear() {
        MDC.remove(CORRELATION_ID_KEY);
    }
}
