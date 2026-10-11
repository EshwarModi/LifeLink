# LifeLink — Blood Donation Platform

A 100% Java Full-Stack blood donation matching platform built with **Java 21**, **Spring Boot 3.3.x**, **Thymeleaf**, **Spring Data JPA/Hibernate**, **Spring Security 6**, **Flyway**, and **MySQL**.

---

## 1. Architecture Overview

LifeLink follows a strict **Package-by-Layer** pattern. All form submissions are bound through validated Data Transfer Objects (DTOs), never directly to JPA entities. Controllers stay thin, while the domain business logic resides entirely in the service layer.

```mermaid
flowchart TD
    Client["Browser / REST Client"] --> FilterChain["Spring Security Filter Chain"]

    subgraph Security ["Security & Observability"]
        FilterChain --> RateLimit["RateLimitingFilter\n(Bucket4j: 10/min Login, 5/min Register)"]
        RateLimit --> CsrfAuth["CSRF Verification & BCrypt Auth"]
        Actuator["/actuator/health\nLiveness & Readiness Probes"]
        SecLogger["Structured Security Audit Logger\n(LOGIN_FAILURE, MATCH_ACCEPTED)"]
    end

    subgraph Controllers ["Controller Layer (Spring MVC)"]
        HomeController["HomeController (/)"]
        AuthController["AuthController (/login, /register)"]
        DashboardController["DashboardController (/dashboard, /donor, /seeker)"]
        RequestController["RequestController (/requests)"]
        MatchController["MatchController (/matches)"]
        DonorController["DonorController (/donor/profile, /availability)"]
        AdminController["AdminController (/admin/dashboard)"]
        ApiController["ApiController (/api/requests, /api/donors)"]
    end

    subgraph Services ["Service Layer (Business Logic)"]
        UserService["UserService\n(Duplicate Checks, BCrypt)"]
        SeekerService["SeekerRequestService\n(Create/Edit Validation Parity)"]
        MatchingEngine["MatchingService\n(3-Tier Location Fallback Engine)"]
        DonorService["DonorService\n(90-Day Eligibility & Availability)"]
        NotifService["NotificationService\n(JavaMailSender, Fail-Safe)"]
    end

    subgraph Scheduler ["Background Schedulers"]
        Job["RequestExpirationScheduler\n(@Scheduled: Auto-Expires Overdue Requests & Matches)"]
    end

    subgraph DataAccess ["Data Layer"]
        UserRepo["UserRepository"]
        ProfileRepo["DonorProfileRepository"]
        ReqRepo["SeekerRequestRepository"]
        MatchRepo["MatchRepository"]
        FlywayMigrate["Flyway Database Migrations\n(V1__init.sql)"]
        MySQL[("MySQL 8.0\n(lifelink db)")]
    end

    CsrfAuth --> Controllers
    Controllers --> Services
    Job --> Services
    Services --> DataAccess
    FlywayMigrate --> MySQL
    DataAccess --> MySQL
```

---

## 2. Tech Stack Overview

- **Language / Target**: Java 21 LTS
- **Core Framework**: Spring Boot 3.3.5 (Spring MVC, Data JPA, Security 6, Validation, Mail, Actuator)
- **View Layer**: Server-Rendered Thymeleaf with custom design system (`style.css`), responsive layouts, and zero external SPA dependencies
- **Database & Schema**: MySQL 8.0 with Flyway migrations (`spring.jpa.hibernate.ddl-auto=validate`)
- **Security**: Spring Security 6, BCrypt (cost factor 12), CSRF protection enforced everywhere, session fixation migration, Bucket4j rate limiting
- **Scheduling**: Spring `@EnableScheduling` running automated expiration sweeps every 15 minutes
- **Observability**: Spring Boot Actuator (`/actuator/health`), structured security audit logging
- **Containerization**: Multi-stage Docker build with Alpine JRE 21 and Docker Compose

---

## 3. Core Design Decisions Built In

1. **CSRF Protection ON Everywhere**: Never disabled on any route or form; CSRF tokens embedded in all forms and client fetch headers.
2. **Session Fixation Defense**: Enforces Spring Security's `migrateSession()` upon login.
3. **Password Policy**: Strict regex validation (`^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).{8,}$`) requiring minimum 8 characters with at least one uppercase letter, one lowercase letter, and one digit; hashed via BCrypt.
4. **Rate Limiting**: `RateLimitingFilter` applies Bucket4j token buckets per client IP (10 attempts/min on `POST /login`, 5 attempts/min on `POST /register`), returning `HTTP 429 Too Many Requests`.
5. **Canonical Match Acceptance**: Single `POST /matches/{requestId}/accept` endpoint with ownership check and atomic upsert semantics.
6. **Validation Parity for Blood Requests**: Creation and editing enforce identical business validation rules (units 1–20, required-by strictly in the future, non-blank hospital details).
7. **Privacy by Default**: Phone numbers and private contacts are hidden in public listings and alerts, disclosed **only** after mutual match acceptance (`contactShared = true`).
8. **Three-Tier Location-Aware Matching** (configured via `lifelink.matching.min-threshold=3` and `lifelink.matching.max-matches=25`):
   - *Tier 1 (City)*: Matches **all** eligible donors in the exact same city, up to the safety cap (`25`).
   - *Tier 2 (State, excluding city)*: Runs only if total matches `< 3`; matches all eligible donors in the same state, up to the safety cap (`25`).
   - *Tier 3 (Nationwide, excluding state)*: Runs only if total matches `< 3`; tops up only until the total reaches the minimum threshold (`3`).
9. **Donor Eligibility**: Available to donate AND (no prior donation OR last donation $\ge 90$ days ago).
10. **Availability Cascading**: When a donor toggles available to `false`, all their `PENDING` matches immediately cascade to `EXPIRED`.
11. **Automated Scheduled Expiration**: Periodic background job auto-expires overdue `OPEN` requests and flips their `PENDING` matches to `EXPIRED`.
12. **Fail-Safe Notifications**: JavaMailSender notification failures are gracefully handled and never abort the database transaction.

---

## 4. Setup & Running Instructions

### Option A: Running via Docker Compose (Recommended)

Requires Docker Desktop or Docker Engine with Docker Compose.

1. **Build and start the complete stack (App + MySQL):**
   ```bash
   docker compose up --build
   ```

2. **Access the application:**
   - Web Platform: [http://localhost:8080](http://localhost:8080)
   - Health Probe: [http://localhost:8080/actuator/health](http://localhost:8080/actuator/health)
   - Seeded Admin: `admin` / `Admin@12345` (redirects to `/admin/dashboard`)

3. **Stop the stack:**
   ```bash
   docker compose down
   ```

#### Docker Compose Environment Variable Mapping

| Application Variable (`app`) | MySQL Variable (`mysql`) | Value in `docker-compose.yml` | Fallback in `application.properties` |
|------------------------------|--------------------------|--------------------------------|--------------------------------------|
| `DB_HOST`                    | *(Service hostname)*     | `mysql`                        | `localhost`                          |
| `DB_PORT`                    | *(MySQL Port)*           | `3306`                         | `3306`                               |
| `DB_NAME`                    | `MYSQL_DATABASE`         | `lifelink`                     | `lifelink`                           |
| `DB_USER`                    | `MYSQL_USER`             | `lifelink`                     | `root`                               |
| `DB_PASSWORD`                | `MYSQL_PASSWORD`         | `lifelink_secret`              | `root`                               |

---

### Option B: Running Locally with Maven & Local MySQL

#### 1. Prerequisites
- **Java 21 LTS** (`java -version`)
- **Maven 3.9+** (`mvn -v`)
- **MySQL 8.0** running locally on port 3306

#### 2. Create MySQL Database
```sql
CREATE DATABASE IF NOT EXISTS lifelink CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

#### 3. Configure Environment Variables (PowerShell example)
```powershell
$env:DB_HOST="localhost"
$env:DB_PORT="3306"
$env:DB_NAME="lifelink"
$env:DB_USER="root"
$env:DB_PASSWORD="your_mysql_password"
$env:ADMIN_USERNAME="admin"
$env:ADMIN_PASSWORD="Admin@12345"
$env:ADMIN_EMAIL="admin@lifelink.org"
```

#### 4. Run the Application
```powershell
mvn spring-boot:run
```
Flyway automatically executes [`V1__init.sql`](file:///c:/yuvi/LifeLink/src/main/resources/db/migration/V1__init.sql), verifies schema tables, and starts the web server on port 8080.

---

## 5. Testing Suite

The project includes 109 automated tests spanning pure unit tests, Bean Validation tests, and full MockMvc integration tests using the isolated H2 test profile (`@ActiveProfiles("test")`).

```powershell
# Run the complete test suite
mvn clean test

# Run specific unit test suites
mvn test -Dtest=MatchingServiceUnitTest
mvn test -Dtest=DonorServiceUnitTest
mvn test -Dtest=SeekerRequestServiceUnitTest
mvn test -Dtest=PasswordPolicyValidationTest

# Run MockMvc flow and CSRF rejection integration tests
mvn test -Dtest=MockMvcFlowAndCsrfIntegrationTest
```

---

## 6. Observability & Security Audit Logging

### Health Probes (`/actuator/health`)
LifeLink exposes only the `health` endpoint (`management.endpoints.web.exposure.include=health`) and its liveness/readiness sub-paths (`/actuator/health/liveness`, `/actuator/health/readiness`):
```http
GET http://localhost:8080/actuator/health
```
- **Anonymous / Unauthenticated Probe Response** (used by Docker/Compose healthchecks):
  ```json
  {
    "status": "UP"
  }
  ```
- **Authenticated `ROLE_ADMIN` Response** (`management.endpoint.health.show-details=when-authorized`, `management.endpoint.health.roles=ADMIN`):
  ```json
  {
    "status": "UP",
    "components": {
      "db": {
        "status": "UP",
        "details": {
          "database": "MySQL",
          "validationQuery": "isValid()"
        }
      },
      "diskSpace": {
        "status": "UP"
      },
      "ping": {
        "status": "UP"
      }
    }
  }
  ```

### Structured Security Audit Logs
Sensitive authentication and privacy-critical transitions emit structured audit entries:

- **Login Failure**:
  ```text
  [SECURITY_AUDIT] event=LOGIN_FAILURE username="attacker" ip="192.168.1.5" reason="Bad credentials"
  ```
- **Login Success**:
  ```text
  [SECURITY_AUDIT] event=LOGIN_SUCCESS username="john_donor" authorities="[ROLE_DONOR]"
  ```
- **User Registration**:
  ```text
  [SECURITY_AUDIT] event=USER_REGISTERED userId=14 username="alice_seeker" role="SEEKER"
  ```
- **Match Accepted (Contact Revealed)**:
  ```text
  [SECURITY_AUDIT] event=MATCH_ACCEPTED donorId=12 donorUsername="john_donor" requestId=5 matchId=42 contactShared=true seekerId=3
  ```
- **Match Declined**:
  ```text
  [SECURITY_AUDIT] event=MATCH_DECLINED donorId=12 requestId=5 matchId=42
  ```

---

## 7. Known Limitations & Future Work

### 7.1 Matching Modes & ABO/Rh Compatibility (Phase 12 Feature Flag)
LifeLink supports two configurable matching modes via `lifelink.matching.mode` (default: `EXACT`), governed by `lifelink.matching.min-threshold` (default: `3`) and `lifelink.matching.max-matches` (default: `25`):

1. **`EXACT` Mode (Default):**
   - Matching strictly pairs requests with donors of the identical blood group (`O+` to `O+`, `A-` to `A-`).
   - Ensures deterministic queries, simplicity for codebase auditing, and preserves scarce universal supplies.
2. **`COMPATIBLE` Mode (`lifelink.matching.mode=COMPATIBLE`):**
   - Enables red blood cell (packed RBC) compatibility matching via [`BloodCompatibility`](file:///c:/yuvi/LifeLink/src/main/java/com/lifelink/service/BloodCompatibility.java).
   - Candidate ranking: **Exact matches are always ranked first** across the three location tiers. Expansion to compatible non-exact blood groups (e.g. `O-` for an `A+` request) occurs **only if** the exact-match total is below `lifelink.matching.min-threshold` (`3`), applying the identical three-tier rules and `lifelink.matching.max-matches` (`25`) cap.
   - Preserves location tier priority (City $\rightarrow$ State $\rightarrow$ Nationwide) within each blood group phase.
   - Donors see a contextual badge in their dashboard: `Compatible match: your O- blood can be used for an A+ request`.

> [!CAUTION]
> **Medical & Transfusion Caveat:**
> Compatibility in this platform covers **packed red blood cell (RBC) donation only**. Plasma and platelet donations follow inverse or different compatibility rules (e.g. AB is universal plasma donor, not O). Furthermore, final medical cross-matching and clinical donor screening are always performed directly by hospitals and certified blood banks, not by this software application.

### 7.2 Single-Node In-Memory Rate Limiting
- The Bucket4j filter uses an in-memory `ConcurrentHashMap`. In a multi-instance horizontally scaled deployment behind a load balancer, rate limits should be backed by Redis (`bucket4j-redis`) to share token buckets across nodes.

### 7.3 Direct SMTP Dispatch
- Email notifications run synchronously within `NotificationService` (with timeouts). In high-volume production systems, dispatching notifications through an asynchronous message broker (e.g., RabbitMQ, Kafka, or Spring `@Async`) decouples HTTP request threads from external SMTP latency.
