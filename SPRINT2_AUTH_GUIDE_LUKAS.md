# Sprint 2 Integration Guide for Lukas: Authentication & User Management

**Target Assignee:** Lukas (Security & Algorithms)  
**Author / Collaborator:** Miles (UI & Data Architect)  
**Relevant Tickets:** AUTH-01, AUTH-02, AUTH-03, AUTH-04 (from Product Backlog)

---

## 1. Overview & Context

This guide outlines how to hook your Sprint 2 backend security and client authentication tasks (**AUTH-01**, **AUTH-02**, **AUTH-03**) into the database schema and JavaFX frontend architecture built by Miles in Sprint 1 and Sprint 2.

### What Miles Built
1. **Database Schema (`wudjabudja/Code/database/schema.sql`):** The `users` table is defined in PostgreSQL with unique email constraints, `password_hash`, `full_name`, and timestamp tracking.
2. **Frontend Views & Client Validation (AUTH-04):**
   * `LoginView.fxml` & `LoginController.java` (`wudjabudja/Code/frontend/src/main/java/com/wudjabudja/controllers/LoginController.java`)
   * `RegisterView.fxml` & `RegisterController.java` (`wudjabudja/Code/frontend/src/main/java/com/wudjabudja/controllers/RegisterController.java`)
   * *Status:* All client-side checks (non-empty fields, `@` and `.` email validation, minimum 6 characters for passwords, and password matching confirmation) are already wired. Placeholders are awaiting your auth manager calls.
3. **Networking Precedent (`PreferenceService.java`):** Standardized on Java 11 `HttpClient` + Jackson `ObjectMapper` targeting `http://localhost:8080`.

---

## 2. Database Mapping (INFRA-01 & AUTH-01)

Your JPA entity must match the table definition in `schema.sql`:

```sql
CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    full_name VARCHAR(100) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
```

### Spring Boot Entity & Repository (`wudjabudja/Code/backend`)

Create these under `com.wudjabudja.wudjabudja_backend.models` (or `user`):

```java
package com.wudjabudja.wudjabudja_backend.models;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public User() {}

    public User(String email, String passwordHash, String fullName) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.fullName = fullName;
        this.createdAt = Instant.now();
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
```

```java
package com.wudjabudja.wudjabudja_backend.repository;

import com.wudjabudja.wudjabudja_backend.models.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
}
```

---

## 3. Backend API Contract (AUTH-01 & AUTH-02)

### Required Maven Dependencies (`wudjabudja/Code/backend/pom.xml`)
Add standard JWT support (such as `jjwt`):
```xml
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-api</artifactId>
    <version>0.12.6</version>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-impl</artifactId>
    <version>0.12.6</version>
    <scope>runtime</scope>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-jackson</artifactId>
    <version>0.12.6</version>
    <scope>runtime</scope>
</dependency>
```

### Endpoints Specification

#### 1. Registration (`POST /api/auth/register`)
* **Request:**
  ```json
  {
    "fullName": "Miles Baack",
    "email": "miles@example.com",
    "password": "secretpassword"
  }
  ```
* **Logic:**
  1. Check if email exists: `if (userRepository.existsByEmail(req.email()))`.
  2. If duplicate, throw an exception routed through your `GlobalExceptionHandler` (400 Bad Request or 409 Conflict with message `"Email is already registered"`).
  3. Hash password using `BCryptPasswordEncoder.encode(req.password())`.
  4. Save user to DB.
* **Response (201 Created):**
  ```json
  {
    "token": "eyJhbGciOi...",
    "userId": 1,
    "email": "miles@example.com",
    "fullName": "Miles Baack"
  }
  ```

#### 2. Login (`POST /api/auth/login`)
* **Request:**
  ```json
  {
    "email": "miles@example.com",
    "password": "secretpassword"
  }
  ```
* **Logic:**
  1. Find user by email; verify with `BCryptPasswordEncoder.matches(req.password(), user.getPasswordHash())`.
  2. If invalid credentials, throw 401 Unauthorized / 400 Bad Request (`"Invalid email or password"`).
  3. Generate a 24-hour expiration JWT.
* **Response (200 OK):**
  ```json
  {
    "token": "eyJhbGciOi...",
    "userId": 1,
    "email": "miles@example.com",
    "fullName": "Miles Baack"
  }
  ```

#### 3. Spring Security Filter Chain
Make sure your security config permits `/api/auth/**` publicly while protecting all `/api/**` business endpoints:
```java
@Bean
public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    http.csrf(csrf -> csrf.disable())
        .authorizeHttpRequests(auth -> auth
            .requestMatchers("/api/auth/**").permitAll()
            .anyRequest().authenticated()
        )
        // Add your JWT filter here before UsernamePasswordAuthenticationFilter
        .sessionManagement(sess -> sess.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
    return http.build();
}
```

---

## 4. JavaFX Auth Manager (AUTH-03)

Create `AuthManager.java` inside `wudjabudja/Code/frontend/src/main/java/com/wudjabudja/services/AuthManager.java`. It aligns with the Jackson + Java 11 `HttpClient` setup in `PreferenceService.java`:

```java
package com.wudjabudja.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

public class AuthManager {

    private static AuthManager instance;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String baseUrl = "http://localhost:8080";

    // In-Memory Session Storage
    private String token;
    private Long currentUserId;
    private String currentEmail;
    private String currentFullName;

    private AuthManager() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();
        this.objectMapper = new ObjectMapper();
    }

    public static synchronized AuthManager getInstance() {
        if (instance == null) {
            instance = new AuthManager();
        }
        return instance;
    }

    public AuthResult login(String email, String password) {
        return sendAuthRequest("/api/auth/login", Map.of(
                "email", email,
                "password", password
        ));
    }

    public AuthResult register(String fullName, String email, String password) {
        return sendAuthRequest("/api/auth/register", Map.of(
                "fullName", fullName,
                "email", email,
                "password", password
        ));
    }

    private AuthResult sendAuthRequest(String endpoint, Map<String, String> payload) {
        try {
            String json = objectMapper.writeValueAsString(payload);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + endpoint))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(5))
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                JsonNode root = objectMapper.readTree(response.body());
                this.token = root.path("token").asText();
                this.currentUserId = root.path("userId").asLong();
                this.currentEmail = root.path("email").asText();
                this.currentFullName = root.path("fullName").asText();
                return new AuthResult(true, "Success");
            } else {
                JsonNode root = objectMapper.readTree(response.body());
                String message = root.has("message") ? root.get("message").asText() : "Authentication failed (Status " + response.statusCode() + ").";
                return new AuthResult(false, message);
            }
        } catch (Exception e) {
            return new AuthResult(false, "Cannot connect to server. Ensure backend is running.");
        }
    }

    public void logout() {
        this.token = null;
        this.currentUserId = null;
        this.currentEmail = null;
        this.currentFullName = null;
    }

    public boolean isAuthenticated() {
        return token != null && !token.isBlank();
    }

    public String getToken() {
        return token;
    }

    public Long getCurrentUserId() {
        return currentUserId;
    }

    public String getCurrentEmail() {
        return currentEmail;
    }

    public String getCurrentFullName() {
        return currentFullName;
    }

    /**
     * Helper for downstream services (e.g., PreferenceService) to attach Bearer token.
     */
    public void attachAuthHeader(HttpRequest.Builder requestBuilder) {
        if (isAuthenticated()) {
            requestBuilder.header("Authorization", "Bearer " + token);
        }
    }

    public record AuthResult(boolean success, String message) {}
}
```

---

## 5. Hooking into the UI Controllers

Once `AuthManager` is created, hook it into `LoginController` and `RegisterController`:

### 1. `LoginController.java` (`wudjabudja/Code/frontend/src/main/java/com/wudjabudja/controllers/LoginController.java`)
Replace lines 47–53 in `handleLogin`:

```java
// --- Replace lines 47-53 ---
AuthManager.AuthResult result = AuthManager.getInstance().login(email, password);
if (result.success()) {
    switchScene(event, "/com/wudjabudja/views/MainView.fxml");
} else {
    showError(result.message());
}
```

### 2. `RegisterController.java` (`wudjabudja/Code/frontend/src/main/java/com/wudjabudja/controllers/RegisterController.java`)
Replace lines 68–71 in `handleRegister`:

```java
// --- Replace lines 68-71 ---
AuthManager.AuthResult result = AuthManager.getInstance().register(fullName, email, password);
if (result.success()) {
    // Transition straight to the Main Shell
    switchScene(event, "/com/wudjabudja/views/MainView.fxml");
} else {
    showError(result.message());
}
```

---

## 6. Downstream Service Integration (e.g. `PreferenceService`)

When George and Ethan hit authenticated endpoints, they can attach the Bearer token using `AuthManager.getInstance().attachAuthHeader(...)`. For instance, in `PreferenceService.java`:

```java
HttpRequest.Builder builder = HttpRequest.newBuilder()
        .uri(URI.create(baseUrl + "/api/trips/" + tripId + "/preferences"))
        .header("Content-Type", "application/json")
        .timeout(Duration.ofSeconds(2))
        .POST(HttpRequest.BodyPublishers.ofString(jsonPayload));

// Attach JWT Bearer token:
AuthManager.getInstance().attachAuthHeader(builder);

HttpRequest httpRequest = builder.build();
```

---

## 7. Verification Checklist for Lukas
- [ ] **Run Backend:** `./mvnw spring-boot:run` in `wudjabudja/Code/backend`.
- [ ] **Run Frontend:** `mvn javafx:run` in `wudjabudja/Code/frontend`.
- [ ] **Test Register:** Register a new user from the UI → Verify user row appears in PostgreSQL (`users` table).
- [ ] **Test Duplicate Email:** Try registering the same email again → Verify `errorLabel` turns red with the backend conflict message.
- [ ] **Test Login:** Log in with correct credentials → Verify transition to `MainView.fxml`.
- [ ] **Test Bad Password:** Log in with an incorrect password → Verify error message displays on `errorLabel`.
