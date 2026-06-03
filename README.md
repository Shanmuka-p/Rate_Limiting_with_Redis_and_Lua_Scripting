# Distributed Rate Limiting Service with Spring Boot & Redis

A distributed rate limiting service built using **Spring Boot**, **Redis**, and **Lua Scripting**. This project implements the **Sliding Window Log** algorithm to protect REST APIs from overuse, malicious traffic, and abuse. 

By leveraging Redis's atomic operations with Lua scripting, checking and incrementing request counts is executed as a single transaction on the Redis server, preventing race conditions in high-concurrency environments.

---

## Technical Stack & Architecture

- **Core Framework**: Spring Boot 3.2.5 (Java 17)
- **Data Store**: Redis 7 (using Sorted Sets for request logs and Hashes for configurations)
- **Scripting**: Lua (to execute atomic checks and writes)
- **Containerization**: Docker (for running the Redis database instance)
- **Build Tool**: Apache Maven (configured locally)

### Sliding Window Log Algorithm
Unlike a fixed window rate limiter (which can suffer from traffic bursts at the boundary lines), the Sliding Window Log keeps a precise rolling log of timestamps for each user request. 
1. Older logs outside the sliding window (`currentTime - windowSize`) are removed using `ZREMRANGEBYSCORE`.
2. The remaining items in the Sorted Set are counted using `ZCARD`.
3. If the count is below the allowed limit, the new request timestamp is added to the Sorted Set using `ZADD`, the set is refreshed with an `EXPIRE` time, and the request is allowed.
4. Otherwise, the request is denied.

---

## Getting Started

### Prerequisites
- **Java 17** installed (available under your `JAVA_HOME` environment variable).
- **Docker Desktop** installed and running.

---

### Step 1: Start Redis
Run the `setup-redis.sh` shell script to pull and start a Redis container.

```bash
chmod +x setup-redis.sh
./setup-redis.sh
```

*(For Windows environments without bash, you can spin up the container manually using the command: `docker run --name rate-limiter-redis -p 6379:6379 -d redis:7`)*

---

### Step 2: Build & Run the Application
Build and launch the Spring Boot application using the provided local Maven wrapper:

```bash
# To compile and run unit/integration tests:
.maven/apache-maven-3.9.6/bin/mvn.cmd clean test

# To start the Spring Boot REST API server:
.maven/apache-maven-3.9.6/bin/mvn.cmd spring-boot:run
```

The application will start on port `8085` (e.g., `http://localhost:8085`).

---

## API Endpoints Reference

### 1. Protected Data Endpoint
Simulates a protected resource. Requires the `X-API-Key` header. Returns `200 OK` on success, or `429 Too Many Requests` when the limit is exceeded.

- **URL**: `/api/data`
- **Method**: `POST`
- **Headers**: `X-API-Key: <your-api-key>`
- **Example request**:
  ```bash
  curl -X POST -H "X-API-Key: client-123" http://localhost:8085/api/data
  ```
- **Example Response (200 OK)**:
  ```json
  {
    "status": "success",
    "message": "Request processed successfully."
  }
  ```
- **Response Headers**:
  - `X-RateLimit-Limit`: Total limit configured for this API Key (defaults to `10` requests/min).
  - `X-RateLimit-Remaining`: Number of remaining requests in the rolling 1-minute window.
  - `X-RateLimit-Reset`: Unix epoch millisecond timestamp representing when the oldest request in the window expires.

- **Example Response (429 Too Many Requests)**:
  ```json
  {
    "error": "Too Many Requests"
  }
  ```

---

### 2. Configure Rate Limit
Allows dynamically configuring custom rate limits for individual API keys. Configuration values are persisted in a Redis Hash (`rate-limit-configs`).

- **URL**: `/api/rate-limit/configure`
- **Method**: `POST`
- **Headers**: `Content-Type: application/json`
- **Request Body**:
  ```json
  {
    "apiKey": "client-123",
    "requestsPerMinute": 15
  }
  ```
- **Example request**:
  ```bash
  curl -X POST -H "Content-Type: application/json" -d "{\"apiKey\": \"client-123\", \"requestsPerMinute\": 15}" http://localhost:8085/api/rate-limit/configure
  ```
- **Example Response**:
  ```json
  {
    "status": "success",
    "message": "Rate limit configured successfully",
    "apiKey": "client-123",
    "requestsPerMinute": 15
  }
  ```

---

### 3. Check Rate Limit Status
Queries Redis to calculate the current request count, remaining requests, and the time when the limit will reset.

- **URL**: `/api/rate-limit/status/{apiKey}`
- **Method**: `GET`
- **Example request**:
  ```bash
  curl http://localhost:8085/api/rate-limit/status/client-123
  ```
- **Example Response**:
  ```json
  {
    "limit": 15,
    "remaining": 14,
    "reset": 1780482560000
  }
  ```

---

### 4. Reset Rate Limit
Manually clears the request log and configuration for a specific API key from Redis.

- **URL**: `/api/rate-limit/reset/{apiKey}`
- **Method**: `DELETE`
- **Example request**:
  ```bash
  curl -X DELETE http://localhost:8085/api/rate-limit/reset/client-123
  ```
- **Example Response**:
  ```json
  {
    "status": "success",
    "message": "Rate limit reset successfully for apiKey: client-123"
  }
  ```

---

## Error Handling
The application handles errors gracefully. If Redis becomes unavailable, the `GlobalExceptionHandler` logs the issue and returns an `HTTP 500 Internal Server Error` with a clear message:
```json
{
  "error": "An internal error occurred: Redis connection failed"
}
```
