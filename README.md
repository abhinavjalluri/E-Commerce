# e-commerce-store

Spring Boot microservice-based application to manage orders, products, inventory and product reviews.

# Microservices Overview

- **API Gateway:** Routes requests to appropriate microservices.
- **Eureka Service:** Service discovery and registration.
- **User Service:** Handles user management.
- **Auth Service:** Provides authentication.
- **Inventory Service:** Manages product inventory and availability.
- **Order Service:** Manage and process customer orders.
- **Reviews Service:** Gather and display product reviews and ratings from users.
- **Notification Service:** Sends notifications to users.

# Technologies and Concepts Used

- Java 17
- Spring Boot
- Maven
- PostgreSQL

### Architecture

- Microservices
- API Gateway Pattern: An `API Gateway` on the edge of the microservices.
- Service Registration and Discovery: using `Netflix Eureka` for service registration and discovery.

### Security

- JWT Tokens: Used for authentication and authorization.


### Event-Driven Messaging

- Kafka

### Observability

- Grafana: Data visualization.
- OpenTelemetry: Collect metrics, traces, and logs.
- Grafana Loki: `Logging`.
- Grafana Tempo and Zipkin: `Distributed Tracing`.
- Prometheus: `Metrics`.

---

### Docker Compose

```bash
  docker compose -f docker-compose-dev.yaml up -d --build
  
  docker compose -f docker-compose-dev.yaml down -v   
```

---

### Exploring and Interacting with API

- **Swagger:** http://localhost:8080/swagger
- **Postman
  Collection:** [postman.json](https://github.com/abhinavjalluri/E-Commerce/blob/main/docs/postman.json)

