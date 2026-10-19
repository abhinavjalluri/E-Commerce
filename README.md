# 🛒 E-Commerce Store — Microservices Platform

A production-grade **Spring Boot microservices** e-commerce system for managing users, authentication, inventory, orders, reviews, and notifications.

It demonstrates a complete event-driven distributed architecture with:

- **API Gateway** (edge routing + JWT auth + rate limiting)
- **Service Discovery** (Netflix Eureka)
- **JWT Security** (stateless auth with Redis-backed validation)
- **Redis** (token validation, rate limiting, caching)
- **Event-Driven Messaging** (Kafka)
- **Full Observability** (OpenTelemetry, Prometheus, Grafana, Loki, Tempo, Zipkin)

---

## 📚 Table of Contents

1. [Architecture Overview](#-architecture-overview)
2. [Tech Stack](#-tech-stack)
3. [Microservices](#-microservices)
4. [Redis Usage (Deep Dive)](#-redis-usage-deep-dive)
5. [Prerequisites](#-prerequisites)
6. [Quick Start](#-quick-start)
7. [End-to-End Flow Test](#-end-to-end-flow-test)
8. [API Reference](#-api-reference)
9. [Observability](#-observability)
10. [Database Inspection](#-database-inspection)
11. [Kafka Inspection](#-kafka-inspection)
12. [Useful Docker Commands](#-useful-docker-commands)
13. [Troubleshooting](#-troubleshooting)

---

## 🏗 Architecture Overview

```
                         ┌────────────────────┐
                         │     Client /       │
                         │   Postman/Swagger  │
                         └─────────┬──────────┘
                                   │
                                   ▼
                        ┌──────────────────────┐
                        │     API Gateway      │  ← JWT auth, rate limit (Redis)
                        │   localhost:8080     │
                        └──────────┬───────────┘
                                   │ (Eureka LB)
        ┌───────────────┬──────────┼───────────┬──────────────┬──────────────┐
        ▼               ▼          ▼           ▼              ▼              ▼
   ┌─────────┐   ┌───────────┐ ┌────────┐ ┌──────────┐ ┌──────────┐ ┌──────────────┐
   │  User   │   │   Auth    │ │Inventory│ │  Order   │ │ Reviews  │ │ Notification │
   │ Service │   │  Service  │ │Service │ │ Service  │ │ Service  │ │   Service    │
   └────┬────┘   └─────┬─────┘ └───┬────┘ └────┬─────┘ └────┬─────┘ └──────┬───────┘
        │              │           │           │            │              │
        ▼              │           ▼           ▼            ▼              │
   ┌─────────┐         │      ┌────────┐  ┌────────┐  ┌─────────┐         │
   │db-users │         │      │db-inv. │  │db-orders│ │db-reviews│        │
   └─────────┘         │      └────────┘  └────────┘  └─────────┘         │
                       │           │           │                          │
                       ▼           ▼           ▼                          ▼
                    ┌──────────────────────────────────────┐       ┌───────────────┐
                    │              Redis                   │       │     Kafka     │
                    │  (token validation • rate limit •    │       │ order.created │
                    │   user cache • inventory cache)      │       │     .v1       │
                    └──────────────────────────────────────┘       └───────┬───────┘
                                                                            │
                                                                            ▼
                                                                  ┌───────────────────┐
                                                                  │Notification-Service│
                                                                  │(consumes event,   │
                                                                  │ reads user cache) │
                                                                  └───────────────────┘
```

---

## 🧰 Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 17 |
| Framework | Spring Boot, Spring Cloud Gateway |
| Build | Maven |
| Service Discovery | Netflix Eureka |
| Security | JWT (RS/HS), Redis-backed token validation |
| Database | PostgreSQL 16 (one DB per service) |
| Cache / Rate Limit | **Redis 7.2** |
| Messaging | Apache Kafka (Confluent) + Zookeeper |
| Observability | OpenTelemetry, Prometheus, Grafana, Loki, Tempo, Zipkin |
| Containerization | Docker, Docker Compose |
| Docs | Swagger / OpenAPI 3 |

---

## 🧩 Microservices

| Service | Port (internal) | Purpose |
|---|---|---|
| **api-gateway** | 8080 | Single entry point, routing, JWT prefilter, Redis rate limiting |
| **eureka-service** | 8761 | Service registry & discovery |
| **user-service** | 808x | User CRUD, publishes user cache to Redis |
| **auth-service** | 808x | Login, JWT issue, token validation via Redis |
| **inventory-service** | 808x | Product CRUD, stock management, Redis cache for products |
| **order-service** | 808x | Order creation, calls inventory, publishes `order.created.v1` |
| **reviews-service** | 808x | Product reviews & ratings with order-ownership validation |
| **notification-service** | 8086 | Kafka consumer, reads user cache from Redis, sends email |

All are registered to Eureka and accessed only through the **API Gateway (localhost:8080)**.

---

## 🔴 Redis Usage (Deep Dive)

Redis plays **four critical roles** in this system. It is started with:

```yaml
command: ["redis-server", "--appendonly", "yes", "--maxmemory", "256mb", "--maxmemory-policy", "volatile-lru"]
```

### 1. 🔐 Token Validation (Auth Service)

- On login, the **Auth Service** issues a JWT and stores a **session/refresh record in Redis** keyed by user (e.g. `auth:session:{userId}`).
- The **API Gateway's `AuthenticationPrefilter`** validates every incoming JWT by checking Redis for the corresponding active session before letting the request reach a downstream service.
- On logout (`POST /api/v1/auth/logout`), the session entry is removed → token is effectively revoked instantly.

**Why?** Plain JWTs can't be revoked without waiting for expiry. Redis gives us a fast (~sub-ms) revocation store.

Keys used:
```
auth:session:{userId}     → active session metadata, TTL = token lifetime
auth:blacklist:{jti}      → optional revoked token ids
```

### 2. 🚦 Rate Limiting (API Gateway)

The Gateway uses `Spring Cloud Gateway`'s **`RedisRateLimiter`** with three tiers declared in `ApiGatewayApplication`:

| Rate limiter | Applied to | Key |
|---|---|---|
| `strictRateLimiter` | `POST /auth/login`, `POST /users`, cache refresh | IP (`ipKeyResolver`) |
| `publicRateLimiter` | Anonymous GETs (products, reviews, orders) | IP |
| `userRateLimiter` | Authenticated routes (`/users/me/**`, orders, reviews, logout) | Username (`userKeyResolver`) |

Keys are automatically created by Spring Cloud Gateway, in the form:
```
request_rate_limiter.{key}.tokens
request_rate_limiter.{key}.timestamp
```

**Why?** Protects the platform from brute-force (login), scraping (public GETs), and abuse from a single authenticated user.

### 3. 🧑 User Cache (User Service → consumed by Notification Service)

- **User Service** populates Redis with a lightweight user projection (`id`, `username`, `email`, `name`).
- Key pattern:
  ```
  user:profile:{userId}    → JSON of user profile, TTL ~ 30–60 min
  user:username:{username} → userId  (reverse index)
  ```
- **Notification Service** (Kafka consumer) does **not** call user-service over HTTP. Instead, when it receives `order.created.v1`, it looks up `user:profile:{userId}` in Redis to get the email address needed to send the confirmation email.

**Why?** Decouples services, avoids synchronous fan-out during event processing, and drastically reduces latency on the notification path.

### 4. 📦 Inventory Cache (Inventory Service)

- **Inventory Service** caches product data in Redis:
  ```
  product:{productId}       → JSON product snapshot, TTL ~ 5–10 min
  product:list              → cached page of products (short TTL)
  ```
- Read endpoints (`GET /api/v1/products`, `GET /api/v1/products/{id}`) serve from cache first.
- Writes (`POST /products/add`, stock decrement from orders) **invalidate** the relevant keys.
- **Order Service** also benefits indirectly: when placing an order, it calls Inventory over REST, and Inventory's cache makes those lookups near-instant.

**Why?** Product catalog reads vastly outnumber writes → caching yields huge throughput wins with minimal correctness risk (short TTLs).

### Redis Keys Summary

| Key | Owner | Consumer(s) | Purpose |
|---|---|---|---|
| `request_rate_limiter.*` | API Gateway | API Gateway | Rate limiting |
| `auth:session:{userId}` | Auth Service | API Gateway | JWT validation / revocation |
| `user:profile:{userId}` | User Service | Notification Service, Gateway | User cache |
| `user:username:{username}` | User Service | Auth Service | Username → userId resolution |
| `product:{id}` / `product:list` | Inventory Service | Inventory, Order | Product cache |

### Manual Redis Inspection

```bash
# Open a Redis shell
docker exec -it redis redis-cli

# See all keys (dev only)
KEYS *

# Inspect a specific user cache entry
GET user:profile:1

# Inspect a rate-limiter bucket
KEYS request_rate_limiter.*
TTL request_rate_limiter.1.tokens

# Inspect active auth sessions
KEYS auth:session:*

# Check Redis health
PING
INFO memory
```

---

## ✅ Prerequisites

- Docker & Docker Compose (v2 recommended)
- Java 17 + Maven (only if running services outside Docker)
- Postman (optional) or `curl`
- A Gmail App Password (for notification-service email) — set as env vars

```bash
export MAIL_USERNAME=your@gmail.com
export MAIL_PASSWORD=your_app_password
```

---

## 🚀 Quick Start

### 1. Build & start everything

```bash
docker compose -f docker-compose-dev.yaml up -d --build
```

### 2. Wait for startup (~60–90s)

```bash
docker compose -f docker-compose-dev.yaml ps
```

### 3. Verify infrastructure

| Tool | URL | Expected |
|---|---|---|
| Eureka | http://localhost:8761 | All services `UP` |
| Gateway health | http://localhost:8080/actuator/health | `{"status":"UP"}` |
| Prometheus | http://localhost:9090 | UI loads |
| Grafana | http://localhost:3000 | Login (`admin/admin`) |
| Zipkin | http://localhost:9411 | UI loads |
| Swagger | http://localhost:8080/swagger | Aggregated API docs |
| Redis | `docker exec -it redis redis-cli ping` | `PONG` |

### 4. Shut down

```bash
# Stop containers (keep volumes/data)
docker compose -f docker-compose-dev.yaml down

# Stop AND wipe all data
docker compose -f docker-compose-dev.yaml down -v
```

---

## 🧪 End-to-End Flow Test

> **Flow:** `User → Auth → Inventory → Order → Kafka → Reviews → Observability`
> **Everything goes through the API Gateway at `http://localhost:8080`.**

### 0️⃣ Sanity Check

Open **http://localhost:8761** → confirm `user-service`, `auth-service`, `inventory-service`, `order-service`, `reviews-service`, `notification-service`, `api-gateway` are all registered.

```bash
# Or via CLI
curl -s http://localhost:8761/eureka/apps | grep -o '"name":"[^"]*"' | sort -u

# Redis health
docker exec -it redis redis-cli ping   # expected: PONG
```

---

### 1️⃣ Create a User (no auth)

```bash
curl -i -X POST http://localhost:8080/api/v1/users \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Michael",
    "email": "michael@gmail.com",
    "username": "michael",
    "password": "password123"
  }'
```

✅ Expect `201 Created`. User is stored in **db-users** and cached in Redis:

```bash
docker exec -it redis redis-cli KEYS 'user:*'
docker exec -it redis redis-cli GET user:profile:1
```

---

### 2️⃣ Login → Get JWT

```bash
curl -i -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "username": "michael",
    "password": "password123"
  }'
```

✅ Expect `Authorization: Bearer <JWT>` in the response headers.

Store it for the rest of the flow:

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"michael","password":"password123"}' \
  | jq -r '.token')

echo "$TOKEN"
```

✅ Confirm the session is in Redis:

```bash
docker exec -it redis redis-cli KEYS 'auth:session:*'
```

> 💡 Postman collection auto-extracts this into `{{token}}`.

---

### 3️⃣ Verify Auth → User Service Integration

```bash
curl -i http://localhost:8080/api/v1/users/me \
  -H "Authorization: Bearer $TOKEN"
```

✅ Confirms: JWT validation → Gateway → Auth → User chain works.

---

### 4️⃣ Add Products (Inventory)

```bash
curl -i -X POST http://localhost:8080/api/v1/products/add \
  -H "Content-Type: application/json" \
  -d '{"name":"iPhone 15","price":999.99,"quantity":100,"description":"Apple phone"}'

curl -i -X POST http://localhost:8080/api/v1/products/add \
  -H "Content-Type: application/json" \
  -d '{"name":"Samsung S24","price":899.99,"quantity":50,"description":"Samsung phone"}'
```

✅ Stored in **db-inventory** and cached in Redis:

```bash
docker exec -it redis redis-cli KEYS 'product:*'
docker exec -it redis redis-cli GET product:1
```

---

### 5️⃣ Get Products (Read Path)

```bash
curl -i http://localhost:8080/api/v1/products
```

✅ Confirms: Inventory DB, Gateway routing, Eureka discovery, and Redis cache.

---

### 6️⃣ Place an Order (Core Business Flow 🔥)

```bash
curl -i -X POST http://localhost:8080/api/v1/orders/users/me \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "products": [
      {"productId": 1, "quantity": 2},
      {"productId": 2, "quantity": 1}
    ]
  }'
```

**What happens internally:**
1. Order Service validates the user (JWT + Redis session).
2. Calls Inventory Service (REST) to check stock.
3. Decreases stock (invalidates `product:{id}` cache in Redis).
4. Saves order in **db-orders**.
5. Publishes Kafka event `order.created.v1`.
6. **Notification-service** consumes the event, reads `user:profile:{userId}` from Redis, sends confirmation email.

✅ This is your distributed transaction. Confirm:
- `docker exec -it db-orders psql -U postgres -d orders -c "SELECT * FROM orders;"`
- `docker exec -it redis redis-cli KEYS 'product:*'` (cached product refreshed/invalidated)

---

### 7️⃣ Verify Kafka

```bash
# Exec into broker
docker exec -it broker bash

# Consume events
kafka-console-consumer \
  --bootstrap-server localhost:29092 \
  --topic order.created.v1 \
  --from-beginning
```

✅ Should print the `order.created.v1` payload(s). Proof that event-driven architecture works.

---

### 7️⃣b Verify Notification Service (Kafka → Redis → Email)

After placing the order, the **notification-service** consumes `order.created.v1`, reads `user:profile:{userId}` from **Redis** (does NOT call user-service), and sends a confirmation email.

```bash
docker logs -f notification-service
```

Verify it read from Redis:
```bash
docker exec -it redis redis-cli GET user:profile:1
```

✅ Confirms: Kafka consumer → Redis cache lookup → email delivery.

---

### 8️⃣ Add a Review (Auth + Order Validation)

```bash
curl -i -X POST http://localhost:8080/api/v1/reviews/users/me \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "productId": 1,
    "orderId": 1,
    "reviewText": "Great product",
    "rating": 5
  }'
```

✅ Confirms: JWT auth + order-ownership validation + Reviews DB write.

Check the databases and the still-valid token session:

```bash
docker exec -it db-users     psql -U postgres -d users
docker exec -it db-orders    psql -U postgres -d orders
docker exec -it db-inventory psql -U postgres -d inventory
docker exec -it db-reviews   psql -U postgres -d reviews

docker exec -it redis redis-cli KEYS 'auth:session:*'
```

---

### 9️⃣ Query Reviews

```bash
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/v1/reviews/products/1
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/v1/reviews/users/me
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/v1/reviews/products/top?max=5
```

---

### 🔟 Verify Notification

The **notification-service** consumes `order.created.v1`, reads `user:profile:{userId}` from Redis, and sends the confirmation email via SMTP.

```bash
docker logs -f notification-service
docker exec -it redis redis-cli GET user:profile:1
```

---

## 📡 API Reference

All via Gateway: `http://localhost:8080`

### Users
| Method | Path | Auth | Rate tier |
|---|---|---|---|
| POST | `/api/v1/users` | ❌ | strict (IP) |
| GET | `/api/v1/users` | ❌ | public (IP) |
| POST | `/api/v1/users/cache/refresh` | ❌ | strict (IP) |
| GET | `/api/v1/users/me` | ✅ | user |
| GET/PUT/DELETE | `/api/v1/users/{id}` | ✅ | user |

### Auth
| Method | Path | Auth | Rate tier |
|---|---|---|---|
| POST | `/api/v1/auth/login` | ❌ | strict (IP) |
| POST | `/api/v1/auth/logout` | ✅ | user |

### Inventory
| Method | Path | Auth | Rate tier |
|---|---|---|---|
| GET | `/api/v1/products` | ❌ | public |
| GET | `/api/v1/products/{id}` | ❌ | public |
| POST | `/api/v1/products/add` | ❌ | public |
| GET | `/api/v1/products/users/me/**` | ✅ | user |

### Orders
| Method | Path | Auth | Rate tier |
|---|---|---|---|
| POST | `/api/v1/orders/users/me` | ✅ | user |
| GET | `/api/v1/orders/users/me` | ✅ | user |
| GET | `/api/v1/orders/{id}` | ❌ | public |

### Reviews
| Method | Path | Auth | Rate tier |
|---|---|---|---|
| POST | `/api/v1/reviews/users/me` | ✅ | user |
| GET | `/api/v1/reviews/products/{productId}` | ❌ | public |
| GET | `/api/v1/reviews/users/me` | ✅ | user |
| GET | `/api/v1/reviews/products/top?max=5` | ❌ | public |

### Docs
- **Swagger aggregator:** http://localhost:8080/swagger
- **Postman collection:** [postman.json](https://github.com/abhinavjalluri/E-Commerce/blob/main/docs/postman.json)

---

## 📊 Observability

### 🔍 Zipkin / Tempo — Distributed Tracing

Open **http://localhost:9411**, search for `order-service`. You'll see traces spanning:
- `api-gateway → inventory-service → order-service`

Tempo is also exposed on `:3200` for Grafana datasource.

### 📈 Prometheus — Metrics

Open **http://localhost:9090**. Try queries:

```promql
http_server_requests_seconds_count
sum(rate(http_server_requests_seconds_count[1m])) by (uri)
histogram_quantile(0.95, sum(rate(http_server_requests_seconds_bucket[5m])) by (le, uri))
```

### 📊 Grafana — Dashboards

1. Open **http://localhost:3000** (`admin` / `admin`).
2. Add datasources (already auto-provisioned via `grafana-datasources.yaml`):
  - Prometheus: `http://prometheus:9090`
  - Loki: `http://loki:3100`
  - Tempo: `http://tempo:3200`
3. Build panels for:
  - Request latency
  - Error rate
  - Logs per service
  - **Redis hits/misses** (`redis_keyspace_hits_total`, `redis_keyspace_misses_total`)

### 🔴 Redis Metrics (Prometheus → Grafana)

Prometheus queries to try:

```promql
redis_keyspace_hits_total
redis_keyspace_misses_total
redis_connected_clients
redis_memory_used_bytes
redis_commands_processed_total
```

Grafana panels to build:
- Redis cache hit ratio: `rate(redis_keyspace_hits_total[5m]) / (rate(redis_keyspace_hits_total[5m]) + rate(redis_keyspace_misses_total[5m]))`
- Redis memory usage
- Rate-limiter 429 count (from `http_server_requests_seconds_count{status="429"}`)

### 📝 Loki — Centralized Logs

Query in Grafana Explore:

```logql
{container="order-service"} |= "ERROR"
{container="api-gateway"} |= "rate"
```

---

## 🗄 Database Inspection

```bash
docker exec -it db-users     psql -U postgres -d users
docker exec -it db-inventory psql -U postgres -d inventory
docker exec -it db-orders    psql -U postgres -d orders
docker exec -it db-reviews   psql -U postgres -d reviews
```

Quick one-liners:

```bash
docker exec -it db-users     psql -U postgres -d users     -c "\dt"
docker exec -it db-orders    psql -U postgres -d orders    -c "SELECT * FROM orders;"
docker exec -it db-inventory psql -U postgres -d inventory -c "SELECT id,name,quantity FROM products;"
docker exec -it db-reviews   psql -U postgres -d reviews   -c "SELECT * FROM reviews;"
```

---

## 📨 Kafka Inspection

```bash
# List topics
docker exec -it broker kafka-topics --bootstrap-server localhost:29092 --list

# Consume from beginning
docker exec -it broker kafka-console-consumer \
  --bootstrap-server localhost:29092 \
  --topic order.created.v1 \
  --from-beginning

# Describe topic
docker exec -it broker kafka-topics \
  --bootstrap-server localhost:29092 \
  --describe --topic order.created.v1

# Consumer groups
docker exec -it broker kafka-consumer-groups \
  --bootstrap-server localhost:29092 --list
```

---

## 🛠 Useful Docker Commands

```bash
# Start
docker compose -f docker-compose-dev.yaml up -d --build

# Stop
docker compose -f docker-compose-dev.yaml down

# Stop + wipe data
docker compose -f docker-compose-dev.yaml down -v

# Status
docker compose -f docker-compose-dev.yaml ps

# Logs
docker logs -f api-gateway
docker logs -f order-service
docker logs -f notification-service

# Rebuild a single service
docker compose -f docker-compose-dev.yaml up -d --build api-gateway

# Redis shell
docker exec -it redis redis-cli

# Kafka shell
docker exec -it broker bash

# Postgres shell
docker exec -it db-orders psql -U postgres -d orders
```

---

## 🩺 Troubleshooting

| Symptom | Fix |
|---|---|
| `503 Service Unavailable` from Gateway | Service not yet registered in Eureka — wait 30–60s, check `http://localhost:8761` |
| `401 Unauthorized` on `/users/me` | Token expired or session evicted from Redis: `docker exec -it redis redis-cli KEYS 'auth:session:*'` |
| `429 Too Many Requests` | Rate limit hit — wait, or inspect `docker exec -it redis redis-cli KEYS 'request_rate_limiter.*'` |
| Products read stale | Cache TTL active — invalidate: `docker exec -it redis redis-cli DEL product:1` |
| Kafka consumer empty | Ensure `init-broker` ran: `docker logs init-broker` |
| Notification email fails | Set `MAIL_USERNAME` and `MAIL_PASSWORD` (Gmail App Password) before `docker compose up` |
| Eureka showing missing service | `docker logs <service-name>` — usually DB or Redis not ready; `depends_on: service_healthy` handles most cases |

---

## ✅ What You've Built

> "This is a Spring Boot microservices system with:
>
> * API Gateway
> * Service discovery (Eureka)
> * JWT security with **Redis-backed token validation & revocation**
> * **Redis rate limiting** (strict / public / per-user tiers)
> * **Redis caching** for users (read by notification-service) and products (read by order-service)
> * Event-driven Kafka messaging (`order.created.v1`)
> * Notification service (Kafka consumer)
> * Distributed tracing (Zipkin / Tempo)
> * Metrics & centralized logging (Prometheus, Grafana, Loki)"

A complete **SRE-grade visibility** production-style stack. 🚀