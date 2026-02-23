# GatePay — Microservices Payment Gateway

A production-ready microservices payment gateway built with Java, Spring Boot, and Spring Cloud. GatePay handles user management, authentication, payments, KYC verification, notifications, and wallet operations across 8 independently deployable services.

---

## Architecture Overview

```
                        ┌─────────────────┐
                        │  API Gateway     │
                        │  (Port 8080)     │
                        │  Rate Limiting   │
                        │  JWT Auth Filter │
                        │  Circuit Breaker │
                        └────────┬────────┘
                                 │
          ┌──────────────────────┼──────────────────────┐
          │                      │                      │
   ┌──────▼──────┐      ┌───────▼──────┐      ┌───────▼──────┐
   │ User Service │      │ Auth Service │      │Payment Service│
   │  (Port 8082) │      │  (Port 8081) │      │  (Port 8083) │
   └─────────────┘      └─────────────┘      └──────────────┘
          │                      │                      │
   ┌──────▼──────┐      ┌───────▼──────┐      ┌───────▼──────┐
   │  KYC Service │      │Notification  │      │  Discovery   │
   │  (Port 8085) │      │  Service     │      │  Service     │
   └─────────────┘      │  (Port 8084) │      │  (Port 8761) │
                        └─────────────┘      └──────────────┘
                                 │
                        ┌────────▼────────┐
                        │    RabbitMQ      │
                        │  Async Messaging │
                        └─────────────────┘
```

---

## Services

| Service | Port | Description |
|---|---|---|
| `discovery-service` | 8761 | Eureka service registry — all services register here |
| `gateway-service` | 8080 | API Gateway — routing, rate limiting, JWT validation |
| `auth-service` | 8081 | Authentication — login, JWT issuance, OTP, password reset |
| `user-service` | 8082 | User management — registration, profiles, roles |
| `payment-service` | 8083 | Payments — Paystack & Flutterwave integration, wallet ops |
| `notification-service` | 8084 | Async notifications — email via RabbitMQ |
| `kyc-service` | 8085 | KYC verification — document upload via Cloudinary |

---

## Tech Stack

- **Language:** Java 17
- **Framework:** Spring Boot 3, Spring Cloud
- **Service Discovery:** Eureka (Netflix)
- **API Gateway:** Spring Cloud Gateway
- **Messaging:** RabbitMQ (async events between services)
- **Caching:** Redis
- **Databases:** MySQL (per service — isolated DBs)
- **Migrations:** Flyway
- **Auth:** JWT (access + refresh tokens)
- **Resilience:** Circuit Breaker (Resilience4j), Feign clients with fallbacks
- **Payment Providers:** Paystack, Flutterwave
- **File Storage:** Cloudinary (KYC documents)
- **Containerization:** Docker, Docker Compose

---

## Getting Started

### Prerequisites

- Docker & Docker Compose
- Java 17+
- Maven 3.9+

### 1. Clone the repository

```bash
git clone https://github.com/benard1991/gate-pay.git
cd gate-pay
```

### 2. Set up environment variables

Each service has a `.env.example` file. Copy and fill in your values:

```bash
cp .env.example .env
cp auth-service/.env.example auth-service/.env
cp user-service/.env.example user-service/.env
cp payment-service/.env.example payment-service/.env
cp kyc-service/.env.example kyc-service/.env
cp notification-service/.env.example notification-service/.env
cp gateway-service/.env.example gateway-service/.env
```

### 3. Run with Docker Compose

```bash
docker-compose up --build
```

Services will start in order. Discovery service comes up first, then all other services register with Eureka.

### 4. Access the services

| Service | URL |
|---|---|
| Eureka Dashboard | http://localhost:8761 |
| API Gateway | http://localhost:8080 |
| Auth Service | http://localhost:8081 |
| User Service | http://localhost:8082 |
| Payment Service | http://localhost:8083 |
| Notification Service | http://localhost:8084 |
| KYC Service | http://localhost:8085 |

---

## Key Features

### Authentication & Security
- JWT-based authentication with access and refresh tokens
- OTP verification for login and password reset
- Inter-service authentication via JWT propagation through the gateway

### Payments
- Paystack and Flutterwave integration with automatic failover via Circuit Breaker
- Wallet management with deposit and withdrawal limits
- Idempotent payment processing to prevent duplicate transactions
- Full audit trail for every transaction

### KYC Verification
- Document upload and storage via Cloudinary
- Admin approval workflow
- Redis-backed idempotency checks

### Notifications
- Fully async email notifications via RabbitMQ
- Services publish events; notification service consumes and dispatches
- No tight coupling between services

### Resilience & Circuit Breaker
- **Circuit Breaker implemented at the API Gateway level** using Spring Cloud Gateway + Resilience4j
- All incoming requests pass through the gateway — if a downstream service is unavailable, the circuit opens and a fallback response is returned immediately
- Prevents cascading failures by stopping traffic to unhealthy service instances
- Circuit Breaker states: `CLOSED` (normal) → `OPEN` (failing, fallback activated) → `HALF_OPEN` (testing recovery)
- **Fallback controller** in the gateway returns a clean error response instead of a timeout or 500 error
- Payment provider resilience: Paystack and Flutterwave calls are protected — if one provider fails, the error is handled gracefully
- Rate limiting at the gateway level via Spring Cloud Gateway filters

---

## Project Structure

```
gate-pay/
├── docker-compose.yml
├── pom.xml                    # Parent POM
├── .env.example               # Root environment template
├── discovery-service/         # Eureka Server
├── gateway-service/           # API Gateway + JWT filter
├── auth-service/              # Auth + OTP + JWT
├── user-service/              # User registration + profiles
├── payment-service/           # Payments + Wallet
├── kyc-service/               # KYC document verification
└── notification-service/      # Async email notifications
```

---

## Environment Variables

Each service uses its own `.env` file. See the `.env.example` in each service directory for the full list of required variables. Never commit `.env` files — they are git-ignored.

---

## Author

**Nwabueze Ifeanyi Benard**  
Senior Backend Engineer  
[nwabuezebenard@gmail.com](mailto:nwabuezebenard@gmail.com)  
Lagos, Nigeria