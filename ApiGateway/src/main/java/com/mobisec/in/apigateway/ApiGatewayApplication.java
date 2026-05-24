package com.mobisec.in.apigateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * API Gateway Application - Single entry point for the Udemy-like learning platform.
 *
 * <p>This gateway handles:
 * <ul>
 *   <li>JWT validation via RSA public key</li>
 *   <li>Request routing to downstream microservices</li>
 *   <li>Rate limiting via Redis</li>
 *   <li>Circuit breaking via Resilience4j</li>
 *   <li>CORS, Correlation IDs, and request logging</li>
 * </ul>
 */
@SpringBootApplication
public class ApiGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayApplication.class, args);
    }
}
