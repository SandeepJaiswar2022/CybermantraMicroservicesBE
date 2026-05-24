# API Gateway — Production-Ready Spring Cloud Gateway

Single entry point for the **Udemy-like Learning Platform** built on Spring Boot microservices.

---

## Architecture Overview

```
React Frontend (localhost:3000 / localhost:5173)
          │
          ▼
  ┌───────────────────────────────────────┐
  │          API Gateway  :8080           │
  │                                       │
  │  ┌─────────────┐  ┌────────────────┐ │
  │  │CorrelationId│  │RequestLogging  │ │
  │  │   Filter    │  │    Filter      │ │
  │  └─────────────┘  └────────────────┘ │
  │                                       │
  │  ┌─────────────────────────────────┐  │
  │  │   JWT Validation (RSA Public)   │  │
  │  └─────────────────────────────────┘  │
  │                                       │
  │  ┌──────────────────────────────────┐ │
  │  │   Redis Rate Limiter             │ │
  │  └──────────────────────────────────┘ │
  │                                       │
  │  ┌──────────────────────────────────┐ │
  │  │   Resilience4j Circuit Breaker   │ │
  │  └──────────────────────────────────┘ │
  └───────────────────────────────────────┘
          │
    ┌─────┼──────────────────────┐
    ▼     ▼          ▼           ▼
 Auth  UserProfile  Course    Content
 :8081   :8082      :8083      :8084
```

### Components

| Component | Purpose |
|---|---|
| `SecurityConfig` | WebFlux security, JWT/RSA validation, CORS |
| `CorrelationIdFilter` | Generates/propagates `X-Request-ID` for tracing |
| `RequestLoggingFilter` | Logs method, path, status, duration per request |
| `RateLimiterConfig` | Redis-backed rate limiting, JWT or IP keyed |
| `CircuitBreakerConfiguration` | Resilience4j circuit breakers for Course & Content services |
| `FallbackController` | Graceful 503 responses when services are down |
| `RedisConfig` | Reactive Lettuce Redis connection factory |

---

## Prerequisites

- Java 17+
- Maven 3.8+ (or use the included `./mvnw`)
- Docker & Docker Compose
- Redis (for rate limiting — via Docker or local install)
- Your Auth Service's **RSA public key** (`public.pem`)

---

## Setup

### 1. Add your RSA Public Key

Replace the placeholder in `src/main/resources/public.pem` with your real public key:

```bash
# If you have the private key your Auth Service uses to sign JWTs:
openssl rsa -in private.pem -pubout -out src/main/resources/public.pem
```

The content should look like:

```
-----BEGIN PUBLIC KEY-----
MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA...
-----END PUBLIC KEY-----
```

### 2. Configure environment variables (optional for local dev)

Copy and edit the dev environment file:

```bash
cp .env.example .env   # or just export variables
```

Default values in `application.yml` work for local development with services on their default ports.

---

## Running Locally

### With Maven (no Docker)

Ensure Redis is running locally on `localhost:6379`, then:

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

Or build and run the JAR:

```bash
./mvnw clean package -DskipTests
java -jar target/api-gateway-1.0.0.jar --spring.profiles.active=dev
```

### With Docker Compose (full stack)

```bash
# Build the gateway image and start all services
docker compose up --build -d

# Watch gateway logs
docker compose logs -f api-gateway

# Stop everything
docker compose down
```

---

## Route Configuration

| Path Pattern | Downstream Service | Rate Limit |
|---|---|---|
| `/api/v1/auth/**` | Auth Service `:8081` | 5 req/min |
| `/api/v1/users/**` | User Profile Service `:8082` | 50 req/min |
| `/api/v1/courses/**` | Course Service `:8083` | 100 req/min |
| `/api/v1/categories/**` | Course Service `:8083` | 100 req/min |
| `/api/v1/content/**` | Content Service `:8084` | 10 req/min |

---

## Security

### Public Endpoints (no JWT required)

```
POST /api/v1/auth/login
POST /api/v1/auth/register
POST /api/v1/auth/refresh
GET  /api/v1/auth/verify-email/**
GET  /actuator/health
GET  /fallback/**
```

### Protected Endpoints

All other routes require a valid JWT in the `Authorization` header:

```
Authorization: Bearer <your_jwt_access_token>
```

The gateway validates:
- JWT signature against the RSA public key
- Token expiry (`exp` claim)
- Forwards the original `Authorization` header to downstream services (each service also validates independently)

---

## Rate Limiting

Rate limits are enforced per authenticated user ID (JWT `sub` claim) or per client IP address for anonymous requests.

Limits are configured per route in `application.yml` using Redis token-bucket algorithm.

To adjust limits, modify the `redis-rate-limiter.*` values under each route's filters section.

---

## Circuit Breakers

| Circuit Breaker | Applies To | Fallback |
|---|---|---|
| `courseServiceCircuitBreaker` | `/api/v1/courses/**`, `/api/v1/categories/**` | `GET /fallback/courses` |
| `contentServiceCircuitBreaker` | `/api/v1/content/**` | `GET /fallback/content` |

**State transitions:**

- Opens after **50–60% failure rate** over a sliding window of 10 calls
- Stays open for **10–20 seconds** before attempting half-open
- Allows **3 test calls** in half-open before fully closing

---

## Actuator Endpoints

| Endpoint | Description |
|---|---|
| `GET /actuator/health` | Service health (public) |
| `GET /actuator/info` | Application info |
| `GET /actuator/metrics` | Micrometer metrics |
| `GET /actuator/prometheus` | Prometheus scrape endpoint |
| `GET /actuator/gateway/routes` | All configured routes |

---

## Correlation ID Tracing

Every request is assigned a `X-Request-ID` header. If the client provides one, it is preserved; otherwise a UUID is generated.

The ID is:
- Forwarded to all downstream services
- Echoed back in the response headers
- Included in all log lines via MDC

To trace a request across services, grep logs by the correlation ID:

```bash
docker compose logs | grep "abc123-correlation-id"
```

---

## Example curl Commands

### Login (public endpoint — no token needed)

```bash
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"user@example.com","password":"secret"}'
```

### Access protected endpoint

```bash
TOKEN="eyJhbGciOiJSUzI1NiJ9..."   # from login response

curl http://localhost:8080/api/v1/courses \
  -H "Authorization: Bearer $TOKEN"
```

### With custom correlation ID

```bash
curl http://localhost:8080/api/v1/users/me \
  -H "Authorization: Bearer $TOKEN" \
  -H "X-Request-ID: my-trace-id-001"
```

### Check gateway health

```bash
curl http://localhost:8080/actuator/health | jq
```

### List configured routes

```bash
curl http://localhost:8080/actuator/gateway/routes | jq
```

---

## Building for Production

```bash
# Build the JAR
./mvnw clean package -DskipTests

# Build and push Docker image
docker build -t mobisec/api-gateway:1.0.0 .
docker push mobisec/api-gateway:1.0.0
```

### Required Environment Variables in Production

| Variable | Description |
|---|---|
| `REDIS_HOST` | Redis hostname |
| `REDIS_PORT` | Redis port (default `6379`) |
| `REDIS_PASSWORD` | Redis password |
| `AUTH_SERVICE_URL` | Auth Service base URL |
| `USER_PROFILE_SERVICE_URL` | User Profile Service base URL |
| `COURSE_SERVICE_URL` | Course Service base URL |
| `CONTENT_SERVICE_URL` | Content Service base URL |
| `SPRING_PROFILES_ACTIVE` | Set to `prod` |

---

## Running Tests

```bash
./mvnw test
```

Test coverage includes:
- `SecurityConfigTest` — verifies public/protected endpoint rules
- `CorrelationIdFilterTest` — verifies ID generation, preservation, and header propagation
- `RateLimiterConfigTest` — verifies KeyResolver IP extraction logic

---

## Project Structure

```
src/
├── main/
│   ├── java/com/mobisec/in/apigateway/
│   │   ├── ApiGatewayApplication.java       ← Entry point
│   │   ├── config/
│   │   │   ├── SecurityConfig.java          ← JWT/RSA, CORS
│   │   │   ├── RedisConfig.java             ← Reactive Redis
│   │   │   └── CircuitBreakerConfiguration.java ← Resilience4j
│   │   ├── filter/
│   │   │   ├── CorrelationIdFilter.java     ← X-Request-ID propagation
│   │   │   └── RequestLoggingFilter.java    ← Access logging
│   │   ├── fallback/
│   │   │   └── FallbackController.java      ← Circuit breaker fallbacks
│   │   └── ratelimiter/
│   │       └── RateLimiterConfig.java       ← KeyResolver (JWT/IP)
│   └── resources/
│       ├── application.yml                  ← Main config + routes
│       ├── application-dev.yml              ← Dev overrides
│       ├── application-prod.yml             ← Prod overrides
│       ├── logback-spring.xml               ← Structured logging
│       └── public.pem                       ← RSA public key (REPLACE THIS)
└── test/
    └── java/com/mobisec/in/apigateway/
        ├── config/SecurityConfigTest.java
        ├── filter/CorrelationIdFilterTest.java
        └── ratelimiter/RateLimiterConfigTest.java
```

---

## Technology Stack

| Technology | Version | Purpose |
|---|---|---|
| Spring Boot | 3.2.5 | Application framework |
| Spring Cloud Gateway | 2023.0.1 | Reactive API Gateway |
| Spring Security WebFlux | 6.x | JWT authentication |
| Nimbus JOSE + JWT | (via OAuth2 RS) | RSA key parsing |
| Redis / Lettuce | 7.x | Rate limiting |
| Resilience4j | 2.x | Circuit breaking |
| Reactor / Project Reactor | 3.x | Reactive programming |
| Micrometer + Prometheus | — | Metrics |
| Logback | — | Structured logging |
| Lombok | — | Boilerplate reduction |
| Docker | — | Containerisation |
