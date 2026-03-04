package com.lms.content.config;

import com.lms.content.util.CorrelationIdUtil;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.UUID;

/**
 * Servlet filter that extracts or generates a correlation ID per request.
 *
 * <p>If the upstream caller (API Gateway, another service) sends an
 * {@code X-Correlation-ID} header, we reuse it. Otherwise we generate a new UUID.
 *
 * <p>The ID is:
 * <ol>
 *   <li>Stored in MDC (for logging)</li>
 *   <li>Returned in the response header (for client-side tracing)</li>
 *   <li>Cleared from MDC after the request completes (prevents leakage in thread pool)</li>
 * </ol>
 *
 * <p>Order(1) ensures this runs before security filters.
 */
@Component
@Order(1)
public class CorrelationIdFilter implements Filter {

    private static final String CORRELATION_HEADER = "X-Correlation-ID";

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
        throws IOException, ServletException {

        HttpServletRequest  httpRequest  = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        String correlationId = httpRequest.getHeader(CORRELATION_HEADER);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }

        CorrelationIdUtil.set(correlationId);
        httpResponse.setHeader(CORRELATION_HEADER, correlationId);

        try {
            chain.doFilter(request, response);
        } finally {
            // CRITICAL: always clear MDC to prevent leakage across thread-pooled requests
            CorrelationIdUtil.clear();
        }
    }
}
