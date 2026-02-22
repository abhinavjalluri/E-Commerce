
This is a **solid setup**, Abhinav — honestly production-grade for a portfolio project 👏
You already have *everything running*. Now I’ll show you **how to use it and test the full end-to-end flow**, step by step, exactly like a real user journey.

I’ll keep this **practical + ordered**, no theory fluff.

---

# 🔁 End-to-End Flow (Happy Path)

We’ll test this flow:

```
User → Auth → Inventory → Order → Kafka → Reviews → Observability
```

All traffic goes through **API Gateway (localhost:8080)** ✅

---

## 0️⃣ Verify Infra Is Healthy (1 min sanity check)

Open these in browser:

| Tool        | URL                                                                            |
| ----------- | ------------------------------------------------------------------------------ |
| Eureka      | [http://localhost:8761](http://localhost:8761)                                 |
| API Gateway | [http://localhost:8080/actuator/health](http://localhost:8080/actuator/health) |
| Prometheus  | [http://localhost:9090](http://localhost:9090)                                 |
| Grafana     | [http://localhost:3000](http://localhost:3000)                                 |
| Zipkin      | [http://localhost:9411](http://localhost:9411)                                 |

👉 In **Eureka**, confirm **ALL services are registered**
(`user-service`, `auth-service`, `inventory-service`, etc.)

If something is missing → logs will tell you.

---

## 1️⃣ Create a User (No Auth Needed)

**POST**

```
http://localhost:8080/api/v1/users
```

Body:

```json
{
  "name": "Michael",
  "email": "michael@gmail.com",
  "username": "michael",
  "password": "password123"
}
```

✅ Expected:

* `201 Created`
* User stored in **db-users**

---

## 2️⃣ Login (Get JWT)

**POST**

```
http://localhost:8080/api/v1/auth/login
```

Body:

```json
{
  "username": "michael",
  "password": "password123"
}
```

✅ Response:

* `Authorization: Bearer <JWT>`

Your Postman test script already:

* extracts token
* stores it as `{{token}}`

🔥 From now on → **everything uses this token**

---

## 3️⃣ Verify Auth → User Service Integration

**GET**

```
http://localhost:8080/api/v1/users/me
```

Headers:

```
Authorization: Bearer {{token}}
```

✅ Confirms:

* JWT validation
* Gateway → Auth → User service chain works

---

## 4️⃣ Add Products (Inventory Service)

**POST**

```
http://localhost:8080/api/v1/products/add
```

Body:

```json
{
  "name": "iPhone 15",
  "price": 999.99,
  "quantity": 100,
  "description": "Apple phone"
}
```

Repeat for 2–3 products.

✅ Stored in **db-inventory**

---

## 5️⃣ Get Products (Read Path)

**GET**

```
http://localhost:8080/api/v1/products
```

✅ Confirms:

* Inventory DB
* Gateway routing
* Service discovery via Eureka

---

## 6️⃣ Place an Order (Core Business Flow 🔥)

**POST**

```
http://localhost:8080/api/v1/orders/users/me
```

Headers:

```
Authorization: Bearer {{token}}
```

Body:

```json
{
  "products": [
    { "productId": 1, "quantity": 2 },
    { "productId": 2, "quantity": 1 }
  ]
}
```

### What happens internally:

1. Order Service validates user
2. Calls Inventory Service (REST)
3. Decreases stock
4. Saves order in **db-orders**
5. Publishes Kafka event:

   ```
   order.created.v1
   ```

✅ This is your **distributed transaction**

---

## 7️⃣ Verify Kafka Is Working

Exec into broker:

```bash
docker exec -it broker-dev bash
```

Consume topic:

```bash
kafka-console-consumer \
  --bootstrap-server localhost:29092 \
  --topic order.created.v1 \
  --from-beginning
```

✅ You should see order events

🔥 This proves **event-driven architecture works**

---

## 8️⃣ Add Review (Auth + Order Validation)

**POST**

```
http://localhost:8080/api/v1/reviews/users/me
```

Headers:

```
Authorization: Bearer {{token}}
```

Body:

```json
{
  "productId": 1,
  "orderId": 1,
  "reviewText": "Great product",
  "rating": 5
}
```

✅ Confirms:

* Auth works
* Order ownership validation
* Reviews DB integration

---

## 9️⃣ Query Reviews

| Action             | Endpoint                             |
| ------------------ | ------------------------------------ |
| Reviews by product | `/api/v1/reviews/products/1`         |
| Reviews by user    | `/api/v1/reviews/users/me`           |
| Top products       | `/api/v1/reviews/products/top?max=5` |

All require:

```
Authorization: Bearer {{token}}
```

---

## 🔍 Observability (This Is Where Your Project Shines)

### 🔹 Zipkin / Tempo

Open:

```
http://localhost:9411
```

Search for:

* `order-service`
* Trace spanning:

   * api-gateway
   * inventory-service
   * order-service

✅ You’ll see **distributed traces**

---

### 🔹 Prometheus

```
http://localhost:9090
```

Try:

```
http_server_requests_seconds_count
```

---

### 🔹 Grafana

```
http://localhost:3000
```

* Add Prometheus datasource
* Add Loki datasource
* View:

   * Request latency
   * Error rate
   * Logs per service

🔥 This is **SRE-grade visibility**

---

## 🧪 Failure Testing (Bonus – Interview Gold)

Try:

* Stop inventory DB → place order
* Reduce stock below requested quantity
* Use expired JWT

Observe:

* HTTP errors
* Traces
* Logs in Loki

---

## ✅ What You’ve Built

> “This is a production-style Spring Boot microservices system with:
>
> * API Gateway
> * Service discovery
> * JWT security
> * Event-driven Kafka messaging
> * Distributed tracing
> * Metrics & centralized logging”

---


