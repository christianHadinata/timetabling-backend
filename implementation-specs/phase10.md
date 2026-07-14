# Phase 10 — Polish, Testing & Docker

> **Reference Roadmap:** [migration-roadmap.md](migration-roadmap.md) → Phase 10
> **Goal:** Make the backend production-ready: a repeatable automated test suite, a reproducible Docker image + `docker-compose` stack, and externalized/hardened configuration.
> **Depends on:** Phases 1–9 — all present (`config/`, `common/`, `auth/`, `semester/`, `jurusan/`, `course/`, `room/`, `lecturer/`, `activity/`, `result/`, `setting/`, `schedule/*`, `common/excel/`).
> **Status:** Ready for execution.

---

## 0. Prerequisites & Do-First Checklist

Green light to implement. Unlike Phases 2–9, this phase adds **no new domain features** — it adds tests, Docker artifacts, and config hardening around the code that already exists.

**Read this first — it changes how every test is written:**

The app runs `spring.jpa.hibernate.ddl-auto=validate` against a live Laravel MySQL database (see [application.properties](../src/main/resources/application.properties)). Per [phase9.md](phase9.md) §0, **that database has never actually been connected**. That has two consequences for Phase 10:

1. The one existing "integration" test that boots Spring — [TimetablingappApplicationTests.java](../src/test/java/com/timetablingapp/TimetablingappApplicationTests.java) `contextLoads()` — **currently cannot pass** on a machine without the MySQL DB running, because `@SpringBootTest` builds the real `DataSource` and Hibernate runs `validate` on startup. Running `./gradlew test` today will fail (or error) at that test. Fixing this is **Task A** below and is a hard prerequisite for "the suite is green".
2. We must not make the whole test suite depend on a hand-maintained MySQL instance. The strategy below keeps the *fast* suite (unit + web-slice) DB-free, and isolates the *real* DB dependency into one **opt-in** Testcontainers test that CI can run but a laptop can skip.

**Checklist before you start:**
- [ ] Confirm Docker Desktop is installed & running (needed for `docker build`, `docker-compose`, and the optional Testcontainers test).
- [ ] Decide whether the first real DB connection happens now (recommended — it retires the "validate unverified" risk that has trailed every phase) or is deferred. See [§7 Decision D1](#d1--test-database-strategy).
- [ ] Have the Laravel schema dump ready (`mysqldump` of the existing DB, or the Laravel `migrations`) — needed both for the `docker-compose` MySQL seed and for the Testcontainers integration test. See [§5.4](#54-schema-seed-for-docker--testcontainers).

---

## 1. Overview

Phase 10 delivers four workstreams. They are independent and can be built in any order, but the recommended order is **A → B → C → D** (get the suite green first, then package it).

| # | Workstream | What it produces |
|---|-----------|------------------|
| **A** | **Test foundation & config** | A test profile that lets Spring tests boot without the production MySQL; test-only dependencies. |
| **B** | **Automated tests** | Unit tests (Mockito, no Spring), web-slice tests (`@WebMvcTest` + MockMvc + Spring Security Test), and one opt-in full-stack Testcontainers test. |
| **C** | **Docker packaging** | `Dockerfile` (multi-stage), `.dockerignore`, `docker-compose.yml` (app + MySQL), schema seed. |
| **D** | **Config hardening / polish** | Externalize secrets & datasource to env vars; production profile; final endpoint/coverage sweep. |

### Design decision — how we test a `ddl-auto=validate` app

Three test "tiers", each with a different Spring footprint. This mirrors the existing codebase, which already contains a **pure-POJO** GA test ([GeneticAlgorithmIntegrationTest.java](../src/test/java/com/timetablingapp/schedule/algorithm/GeneticAlgorithmIntegrationTest.java)) that uses no Spring and no DB — proof this project can be tested without a live database.

| Tier | Spring footprint | DB | Speed | Used for |
|------|------------------|----|-------|----------|
| **Unit** | none (plain JUnit + Mockito) | none | ms | Service business logic: `AuthService`, `SlotActivityService`, `TimetableService.mapResult`, `PairwiseConflictResolver` (already tested), constraint math. |
| **Web slice** | `@WebMvcTest(XController.class)` — controller + `MockMvc` + security filter chain, **services mocked** with `@MockitoBean` | none | ~1s | Controller wiring: routing, JSON (de)serialization, validation (`@Valid` → 400), auth (401/403), status codes. |
| **Full stack** *(opt-in)* | `@SpringBootTest` + Testcontainers MySQL | real MySQL in a container, seeded from the Laravel dump | ~30s+ | `contextLoads`, `ddl-auto=validate` actually passing, one real login→CRUD→persist happy path. Tagged `@Tag("integration")`, excluded from the default `test` task. |

This gives a `./gradlew test` that is **fast and DB-free** for day-to-day work, plus a `./gradlew integrationTest` that a CI job (with Docker) runs to prove the schema really validates.

---

## 2. Files to Add / Edit / Delete

### Add (Docker — 3 files)

```
new/timetabling-backend/
├── Dockerfile                              # multi-stage: Gradle build → JRE 21 runtime
├── .dockerignore                           # keep build context small (exclude build/, .gradle/, .git/)
└── docker-compose.yml                      # app + mysql services, healthcheck, volume
```

### Add (config — 2 files)

```
new/timetabling-backend/src/
├── main/resources/
│   └── application-prod.properties         # prod profile: everything from env vars, ddl-auto=validate, sql off
└── test/resources/
    ├── application-test.properties         # test profile: H2/none datasource, no security surprises
    └── schema/laravel-schema.sql           # (optional) DDL dump used by the Testcontainers test  — see §5.4
```

### Add (tests — 8 files)

```
new/timetabling-backend/src/test/java/com/timetablingapp/
├── support/
│   ├── SecurityTestConfig.java             # imports real SecurityConfig into @WebMvcTest slices
│   └── WithMockJwt.java                     # (optional) helper to stamp a SecurityContext for slice tests
├── auth/
│   ├── AuthServiceTest.java                # UNIT (Mockito) — login success / bad password / unknown user
│   └── AuthControllerTest.java             # WEB SLICE — POST /api/auth/login, GET /api/auth/me, 401
├── course/
│   └── CourseControllerTest.java           # WEB SLICE — CRUD routing, @Valid → 400, faculty filter passthrough
├── room/
│   └── RoomControllerTest.java             # WEB SLICE — CRUD routing + parent/child payload
├── activity/
│   └── ActivityControllerTest.java         # WEB SLICE — create-with-constraints request/response mapping
└── schedule/
    ├── slot/act/
    │   └── SlotActivityServiceTest.java    # UNIT (Mockito) — revalidate/reset/isStale + lock behavior
    └── algorithm/
        └── TimetableServiceIntegrationTest.java  # FULL STACK (Testcontainers, @Tag("integration"))
```

> **Note on the roadmap's `TimetableServiceTest`:** the *algorithmic* correctness of the GA (no lecturer conflicts, no double-booking, no curriculum overlap, locks preserved) is **already covered** by [GeneticAlgorithmIntegrationTest.java](../src/test/java/com/timetablingapp/schedule/algorithm/GeneticAlgorithmIntegrationTest.java) as a pure-POJO test. We therefore do **not** duplicate it. What is *not* covered is `TimetableService` wiring (loading a `Setting`, filtering activities/slots/rooms from the DB, `mapResult`, `save`) — that is what `TimetableServiceIntegrationTest` targets, and it needs a real DB, hence Testcontainers.

### Edit (4 files)

```
new/timetabling-backend/
├── build.gradle.kts                        # add test deps (testcontainers, h2) + integrationTest task
├── src/main/resources/application.properties  # externalize datasource/jwt/ga to ${ENV:default} form
└── src/test/java/com/timetablingapp/
    └── TimetablingappApplicationTests.java # move to @Tag("integration") OR gate on test profile (Task A)
```

### Delete

None. (`HELP.md` is already git-ignored; no source is removed.)

---

## 3. Workstream A — Test Foundation & Config

### 3.1 `build.gradle.kts` — test dependencies + integration task

The current [build.gradle.kts](../build.gradle.kts) already has `spring-boot-starter-test`, `spring-security-test`, and `junit-platform-launcher`. Add H2 (for the light test profile) and Testcontainers (for the opt-in real-MySQL test), plus a separate `integrationTest`-flavored run via JUnit tags.

**Edit — dependencies block (append after line 64):**

```kotlin
    // --- Phase 10: testing ---
    // H2 for the DB-free "test" profile (schema created from entities, not validated against MySQL)
    testRuntimeOnly("com.h2database:h2")

    // Testcontainers — real MySQL for the opt-in @Tag("integration") full-stack test
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:mysql")
```

Spring Boot 4.0.2's BOM manages Testcontainers/H2 versions, so no explicit versions are needed.

**Edit — split the default `test` task from `integrationTest` (replace lines 67-69):**

```kotlin
tasks.withType<Test> {
    useJUnitPlatform()
}

// Fast, DB-free suite (default `./gradlew build` / `./gradlew test`) — skips anything tagged "integration".
tasks.named<Test>("test") {
    useJUnitPlatform {
        excludeTags("integration")
    }
}

// Opt-in suite that needs Docker (Testcontainers MySQL). Run with `./gradlew integrationTest`.
tasks.register<Test>("integrationTest") {
    description = "Runs @Tag(\"integration\") tests (requires Docker)."
    group = "verification"
    useJUnitPlatform {
        includeTags("integration")
    }
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    shouldRunAfter("test")
}
```

> Rationale: keeps `./gradlew build` green on any machine (CI or laptop) without Docker/MySQL, while still allowing a Docker-equipped CI stage to run the real-DB proof via `./gradlew integrationTest`.

### 3.2 `application.properties` — externalize config (Workstream D, but needed for tests too)

Replace hard-coded values with `${ENV_VAR:default}` so the same jar runs locally, in tests, and in Docker without edits. **The defaults preserve today's local behavior**, so nothing breaks for existing dev flow.

**Edit — [application.properties](../src/main/resources/application.properties):**

```properties
# ===========================
# DATASOURCE
# ===========================
spring.datasource.url=${DB_URL:jdbc:mysql://localhost:3306/timetab?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Jakarta}
spring.datasource.username=${DB_USERNAME:root}
spring.datasource.password=${DB_PASSWORD:Receiver1}
spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver

# ===========================
# JPA / HIBERNATE
# ===========================
spring.jpa.hibernate.ddl-auto=${DDL_AUTO:validate}
# ... (rest unchanged) ...

# ===========================
# JWT CONFIGURATION
# ===========================
jwt.secret=${JWT_SECRET:kk01yAiWo4uX2QEd4f8DIUfSBSpN8Jfy34Bld2gVmWI}
jwt.expiration=${JWT_EXPIRATION:86400000}

# ===========================
# CORS (frontend origin)  — read by CorsConfig
# ===========================
app.cors.allowed-origins=${CORS_ORIGINS:http://localhost:5173,http://localhost:3000}
```

> ⚠️ **Verify before shipping:** check that [CorsConfig.java](../src/main/java/com/timetablingapp/config/CorsConfig.java) actually reads `app.cors.allowed-origins`. If it currently hard-codes origins, wire it to this property (`@Value("${app.cors.allowed-origins}")` split on comma) as part of Workstream D. If the property name differs, keep the existing one and only externalize its value.

### 3.3 `application-test.properties` (NEW) — the DB-free test profile

Used by `@SpringBootTest`/slice tests that are **not** tagged integration. Points Hibernate at an in-memory H2 with `create-drop` so no MySQL is required. Because H2 ≠ MySQL, this profile does **not** prove `validate`; that is the integration test's job.

**Add — [src/test/resources/application-test.properties](../src/test/resources/application-test.properties):**

```properties
# In-memory H2 in MySQL-compatibility mode; schema generated from @Entity classes.
spring.datasource.url=jdbc:h2:mem:testdb;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE
spring.datasource.username=sa
spring.datasource.password=
spring.datasource.driver-class-name=org.h2.Driver

spring.jpa.hibernate.ddl-auto=create-drop
spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect
spring.jpa.show-sql=false

# deterministic secret for token tests (>= 256 bits for HS256)
jwt.secret=test-secret-test-secret-test-secret-test-secret-0123456789
jwt.expiration=86400000

ga.population-size=10
ga.generations=10
ga.crossover-rate=0.8
ga.mutation-rate=0.1
```

> **Caveat to flag during implementation:** several entities use MySQL-specific column types / soft-delete `@SQLRestriction` / non-standard FKs (e.g. `Activity.course` joined on `courses.code`, per [migration-roadmap.md](migration-roadmap.md) Phase 5). H2 in MySQL mode handles most of this, but if a particular entity won't map under H2, prefer moving that test to the Testcontainers tier rather than fighting H2. The **web-slice tests below need no datasource at all**, so they sidestep this entirely — which is why they carry most of the coverage.

### 3.4 Task A — fix `TimetablingappApplicationTests`

Today's `contextLoads()` boots the full context against production MySQL and will fail without it. Two acceptable fixes — **pick D1 in §7**:

**Option A1 (recommended):** convert it into the real integration test and gate it on Docker:

```java
package com.timetablingapp;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@Tag("integration")           // excluded from default `test`, run via `./gradlew integrationTest`
@SpringBootTest
@ActiveProfiles("test")       // OR wire Testcontainers (see §4.5) for a real-MySQL boot
class TimetablingappApplicationTests {

    @Test
    void contextLoads() {
        // Verifies the full Spring context starts.
    }
}
```

**Option A2 (lighter):** keep it in the default suite but run it under the H2 `test` profile:

```java
@SpringBootTest
@ActiveProfiles("test")
class TimetablingappApplicationTests { @Test void contextLoads() {} }
```

A2 is faster but only proves the context wires under H2 (not that MySQL validates). A1 is stricter. The plan below assumes **A1** and puts the "does it boot against real MySQL" proof in `TimetableServiceIntegrationTest` / this test under Testcontainers.

---

## 4. Workstream B — Automated Tests

### 4.1 Shared slice-test support

`@WebMvcTest` does **not** load `@Configuration` classes by default except web ones; but our [SecurityConfig.java](../src/main/java/com/timetablingapp/config/SecurityConfig.java) **is** picked up as it's a `WebSecurityConfigurer`. However it depends on `JwtAuthenticationFilter` → `JwtService`, which are not in a controller slice. Provide them (or import config) via a small test config.

**Add — [src/test/java/com/timetablingapp/support/SecurityTestConfig.java](../src/test/java/com/timetablingapp/support/SecurityTestConfig.java):**

```java
package com.timetablingapp.support;

import com.timetablingapp.config.CorsConfig;
import com.timetablingapp.config.JwtAuthenticationFilter;
import com.timetablingapp.config.JwtService;
import com.timetablingapp.config.SecurityConfig;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;

/**
 * Pulls the real security filter chain (+ its JWT collaborators + CORS) into @WebMvcTest slices,
 * so 401/403 behavior is exercised exactly as in production rather than being auto-permitted.
 */
@TestConfiguration
@Import({ SecurityConfig.class, JwtAuthenticationFilter.class, JwtService.class, CorsConfig.class })
public class SecurityTestConfig {
}
```

> During implementation, confirm `JwtAuthenticationFilter`/`JwtService` construct cleanly in a slice (they read `jwt.secret`; `application-test.properties` supplies it, so annotate slices with `@ActiveProfiles("test")`). If they pull in a `UserRepository`, add `@MockitoBean UserRepository` to the slice or mock inside `SecurityTestConfig`.

**Authentication in slice tests:** prefer `@WithMockUser(roles = "ADMIN")` from `spring-security-test` (already a dependency) over minting real JWTs — it stamps the `SecurityContext` directly and keeps tests independent of `JwtService` internals. Use it on methods that must pass the `authenticated()` gate.

### 4.2 `AuthServiceTest` — UNIT (Mockito)

Targets [AuthService.java](../src/main/java/com/timetablingapp/auth/AuthService.java) (`login`) with `UserRepository`, `JwtService`, `PasswordEncoder` all mocked. No Spring.

**Add — [src/test/java/com/timetablingapp/auth/AuthServiceTest.java](../src/test/java/com/timetablingapp/auth/AuthServiceTest.java):**

```java
package com.timetablingapp.auth;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock UserRepository userRepository;
    @Mock JwtService jwtService;
    @Mock PasswordEncoder passwordEncoder;
    @InjectMocks AuthService authService;

    @Test
    void login_success_returnsTokenAndUser() {
        User user = new User();
        user.setEmail("admin@x.com");
        user.setPassword("$2a$hashed");
        when(userRepository.findByEmail("admin@x.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("pw", "$2a$hashed")).thenReturn(true);
        when(jwtService.generateToken(user)).thenReturn("jwt-123");

        AuthResponse res = authService.login(new LoginRequest("admin@x.com", "pw"));

        assertEquals("jwt-123", res.getToken());
        assertEquals("Bearer", res.getTokenType());
        assertNotNull(res.getUser());
    }

    @Test
    void login_wrongPassword_throws() {
        User user = new User();
        user.setEmail("admin@x.com");
        user.setPassword("$2a$hashed");
        when(userRepository.findByEmail("admin@x.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("bad", "$2a$hashed")).thenReturn(false);

        assertThrows(RuntimeException.class,   // narrow to the actual exception type used by AuthService
                () -> authService.login(new LoginRequest("admin@x.com", "bad")));
        verify(jwtService, never()).generateToken(any());
    }

    @Test
    void login_unknownUser_throws() {
        when(userRepository.findByEmail("ghost@x.com")).thenReturn(Optional.empty());
        assertThrows(RuntimeException.class,
                () -> authService.login(new LoginRequest("ghost@x.com", "pw")));
    }
}
```

> **Adapt to reality:** open `AuthService.login` and match (a) the repo method name (`findByEmail` vs `findByEmailAndDeletedAtNull`), (b) the `User` setters/builder, and (c) the exact exception thrown for bad creds (likely `BadRequestException`/`ResourceNotFoundException` from `common/exception`) — replace `RuntimeException.class` accordingly. Also confirm `jwtService.generateToken` signature (`User` vs `email`).

### 4.3 `AuthControllerTest` — WEB SLICE

**Add — [src/test/java/com/timetablingapp/auth/AuthControllerTest.java](../src/test/java/com/timetablingapp/auth/AuthControllerTest.java):**

```java
package com.timetablingapp.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.timetablingapp.support.SecurityTestConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuthController.class)
@Import(SecurityTestConfig.class)
@ActiveProfiles("test")
class AuthControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    @MockitoBean AuthService authService;
    // If JwtAuthenticationFilter needs it to resolve the token principal:
    @MockitoBean UserRepository userRepository;

    @Test
    void login_valid_returns200WithToken() throws Exception {
        when(authService.login(any())).thenReturn(
                AuthResponse.builder().token("jwt-123").user(null).build());

        mvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content(json.writeValueAsString(new LoginRequest("a@x.com", "pw"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("jwt-123"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"));
    }

    @Test
    void login_blankEmail_returns400() throws Exception {
        mvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content(json.writeValueAsString(new LoginRequest("", "pw"))))
                .andExpect(status().isBadRequest());   // @Valid @Email/@NotBlank → GlobalExceptionHandler
    }

    @Test
    void me_withoutAuth_returns401() throws Exception {
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void me_authenticated_returns200() throws Exception {
        when(authService.getCurrentUser()).thenReturn(null); // match real method name/return
        mvc.perform(get("/api/auth/me")).andExpect(status().isOk());
    }
}
```

> `@MockitoBean` is the Spring Boot 3.4+/4.x replacement for the deprecated `@MockBean` — correct for Boot 4.0.2. Confirm `AuthController.me()` delegates to a service method (`getCurrentUser`/`me`) and adjust the stub; if it reads `SecurityContextHolder` directly, drop that stub and rely on `@WithMockUser`.

### 4.4 `CourseControllerTest`, `RoomControllerTest`, `ActivityControllerTest` — WEB SLICE

Same shape as `AuthControllerTest`. Each mocks its service(s) and asserts routing + status + JSON. **`CourseController` needs both `CourseService` and `CourseExcelService` mocked** (verified in source).

**Add — [src/test/java/com/timetablingapp/course/CourseControllerTest.java](../src/test/java/com/timetablingapp/course/CourseControllerTest.java):**

```java
package com.timetablingapp.course;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.timetablingapp.support.SecurityTestConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CourseController.class)
@Import(SecurityTestConfig.class)
@ActiveProfiles("test")
class CourseControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    @MockitoBean CourseService courseService;
    @MockitoBean CourseExcelService courseExcelService;   // controller ctor-injects it
    @MockitoBean com.timetablingapp.auth.UserRepository userRepository; // for the security filter

    @Test
    void getAll_withoutAuth_returns401() throws Exception {
        mvc.perform(get("/api/courses")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getAll_authenticated_returns200() throws Exception {
        when(courseService.findAll()).thenReturn(List.of()); // match real method name
        mvc.perform(get("/api/courses")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void create_invalidBody_returns400() throws Exception {
        mvc.perform(post("/api/courses")
                        .contentType("application/json")
                        .content("{}"))               // missing required fields → @Valid fails
                .andExpect(status().isBadRequest());
    }
}
```

`RoomControllerTest` and `ActivityControllerTest` follow the identical template — swap the `@WebMvcTest` target, the `@MockitoBean` service(s), and the request bodies:

- **RoomControllerTest:** mock `RoomService` (+ `RoomExcelService` if ctor-injected). Assert `GET /api/rooms` 200, `POST` with a parent/child payload maps `parentRoomId`, invalid body → 400.
- **ActivityControllerTest:** mock `ActivityService` (+ `ActivityExcelService`). Assert `POST /api/activities` accepts a request carrying `lecturerNiks`/`roomIds`/`roomTypeIds` and returns those in the response DTO; assert `GET /api/activities?semesterId=1` routes to the semester-filtered method.

> For every slice test, open the target controller first and copy the **exact** service method names and request/response DTO field names — the snippets use placeholder names (`findAll`, etc.) that must match.

### 4.5 `SlotActivityServiceTest` — UNIT (Mockito)

Targets [SlotActivityService.java](../src/main/java/com/timetablingapp/schedule/slot/act/SlotActivityService.java). Verified dependencies: `SlotValidationService`, `SlotActivityRepository`, `ValidateLockService`, `SemesterRepository`. Public methods: `revalidate()`, `reset()`, `isStale()`.

**Add — [src/test/java/com/timetablingapp/schedule/slot/act/SlotActivityServiceTest.java](../src/test/java/com/timetablingapp/schedule/slot/act/SlotActivityServiceTest.java):**

```java
package com.timetablingapp.schedule.slot.act;

import com.timetablingapp.schedule.validate.ValidateLockService;
import com.timetablingapp.semester.SemesterRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SlotActivityServiceTest {

    @Mock SlotValidationService slotValidationService;
    @Mock SlotActivityRepository slotActivityRepository;
    @Mock ValidateLockService validateLockService;
    @Mock SemesterRepository semesterRepository;
    @InjectMocks SlotActivityService service;

    @Test
    void reset_clearsSlotActs() {
        service.reset();
        verify(slotActivityRepository).deleteAll();  // match real clearing call
    }

    // revalidate(): assert it acquires the lock, delegates validation, and releases the lock.
    // isStale():    assert the boolean it derives from lock/semester state.
    // Fill these in against the real method bodies — mock the collaborators' return values,
    // then verify the interaction order (e.g. validateLockService.acquire ... release).
}
```

> This is a **skeleton**: read the three method bodies and assert their real collaborations (lock acquire/release around `revalidate`, `BadRequestException` when a lock is already held, what `isStale` compares). Keep it Mockito-only — no Spring, no DB.

### 4.6 `TimetableServiceIntegrationTest` — FULL STACK (Testcontainers, opt-in)

The only DB-backed test. Boots the real context against a throwaway MySQL container seeded with the Laravel schema, then drives one realistic path. Tagged `integration` so it's excluded from `./gradlew test`.

**Add — [src/test/java/com/timetablingapp/schedule/algorithm/TimetableServiceIntegrationTest.java](../src/test/java/com/timetablingapp/schedule/algorithm/TimetableServiceIntegrationTest.java):**

```java
package com.timetablingapp.schedule.algorithm;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Tag("integration")
@Testcontainers
@SpringBootTest
class TimetableServiceIntegrationTest {

    @Container
    @ServiceConnection   // auto-wires spring.datasource.* to the container — no manual URL plumbing
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("timetab")
            .withInitScript("schema/laravel-schema.sql");   // §5.4 — DDL + minimal seed

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        // The prod default is validate; the container is seeded with the real DDL, so validate must pass.
        r.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @Autowired TimetableService timetableService;

    @Test
    void contextBootsAndValidatesAgainstRealSchema() {
        // If this test's context loads, ddl-auto=validate passed against the Laravel schema — the
        // single most important unproven risk carried since Phase 1.
        org.junit.jupiter.api.Assertions.assertNotNull(timetableService);
    }

    // Optional deeper path (needs seed rows): create a Setting, call generateSync with a no-op
    // listener, assert a non-empty schedule, then save() and read it back via getData().
}
```

> **This test doubles as the migration's `ddl-auto=validate` proof.** If it fails, the failures are the exact column/type mismatches every prior phase deferred — fix the entities (or the seed DDL) until it's green. This is the cheapest place to discover them, before Docker.

---

## 5. Workstream C — Docker Packaging

### 5.1 `Dockerfile` (multi-stage)

**Add — [Dockerfile](../Dockerfile):**

```dockerfile
# ---- Stage 1: build ----
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

# Cache dependencies: copy Gradle wrapper + build files first
COPY gradlew ./
COPY gradle ./gradle
COPY build.gradle.kts settings.gradle.kts ./
RUN chmod +x gradlew && ./gradlew --no-daemon dependencies || true

# Copy sources and build the boot jar (skip tests — CI runs them separately)
COPY src ./src
RUN ./gradlew --no-daemon clean bootJar -x test

# ---- Stage 2: runtime ----
FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app

# Non-root user
RUN groupadd --system app && useradd --system --gid app app
USER app

COPY --from=build /app/build/libs/*.jar app.jar

EXPOSE 8080
ENV JAVA_OPTS=""
# Prod profile: all config comes from env vars injected by docker-compose / the platform.
ENV SPRING_PROFILES_ACTIVE=prod
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
```

> `bootJar` produces `build/libs/timetablingapp-0.0.1-SNAPSHOT.jar` (group/version from [build.gradle.kts](../build.gradle.kts)). The wildcard copy avoids hard-coding the version. `-x test` keeps image builds fast and independent of a DB; tests are the CI's job (§3.1).

### 5.2 `.dockerignore`

**Add — [.dockerignore](../.dockerignore):**

```
.git
.gradle
build
.idea
.vscode
*.iml
implementation-specs
HELP.md
*.log
.env
```

Keeps the build context small (excludes `build/`, `.gradle/`, the specs, IDE cruft) so `docker build` is fast and reproducible.

### 5.3 `docker-compose.yml` (app + MySQL)

**Add — [docker-compose.yml](../docker-compose.yml):**

```yaml
services:
  db:
    image: mysql:8.0
    container_name: timetab-mysql
    environment:
      MYSQL_ROOT_PASSWORD: ${DB_ROOT_PASSWORD:-Receiver1}
      MYSQL_DATABASE: ${DB_NAME:-timetab}
    ports:
      - "3307:3306"                      # host 3307 to avoid clashing with a local MySQL on 3306
    volumes:
      - db-data:/var/lib/mysql
      - ./src/test/resources/schema:/docker-entrypoint-initdb.d:ro   # seeds schema on first boot (§5.4)
    healthcheck:
      test: ["CMD", "mysqladmin", "ping", "-h", "localhost", "-p$$MYSQL_ROOT_PASSWORD"]
      interval: 10s
      timeout: 5s
      retries: 10

  app:
    build: .
    container_name: timetab-app
    depends_on:
      db:
        condition: service_healthy       # wait for DB before validate runs
    environment:
      SPRING_PROFILES_ACTIVE: prod
      DB_URL: jdbc:mysql://db:3306/${DB_NAME:-timetab}?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Jakarta
      DB_USERNAME: root
      DB_PASSWORD: ${DB_ROOT_PASSWORD:-Receiver1}
      DDL_AUTO: validate
      JWT_SECRET: ${JWT_SECRET:-kk01yAiWo4uX2QEd4f8DIUfSBSpN8Jfy34Bld2gVmWI}
      CORS_ORIGINS: ${CORS_ORIGINS:-http://localhost:5173}
    ports:
      - "8080:8080"

volumes:
  db-data:
```

> `depends_on.condition: service_healthy` is what makes `ddl-auto=validate` survive a cold `docker-compose up` — the app won't start until MySQL answers pings. The `app` service reaches the DB by the service name `db`, not `localhost`.

### 5.4 Schema seed for Docker & Testcontainers

Both the compose DB and the Testcontainers test need the Laravel schema to exist before `validate` runs. Produce it once and reuse it:

**Add — [src/test/resources/schema/laravel-schema.sql](../src/test/resources/schema/laravel-schema.sql):**

```sql
-- Generated from the existing Laravel database:
--   mysqldump --no-data --skip-comments timetab > laravel-schema.sql
-- Contains CREATE TABLE for: users, semesters, jurusans, konsentrasi, courses,
-- course_constraints, rooms, room_types, room_availables, lecturers, lecturer_time_n_a_s,
-- activities, activity_types, activity_constraints, activity_paralels, activity_gaps,
-- results, settings, setting_constraints, slots, slot_acts, times, validate_lock.
-- Append minimal INSERTs (one admin user, one current semester) if the integration test drives CRUD.
```

> **Action:** run the `mysqldump --no-data` against the real Laravel DB and paste the output here. If the DB truly isn't reachable yet, the fallback is to run the Laravel migrations into a scratch MySQL and dump that. Mounting the same file at `/docker-entrypoint-initdb.d` (compose) and via `.withInitScript` (Testcontainers) keeps the two environments identical.

---

## 6. Workstream D — Config Hardening & Final Polish

| Task | Detail | Files |
|------|--------|-------|
| **Externalize config** | Done in §3.2 — datasource/JWT/CORS/DDL now `${ENV:default}`. | `application.properties` |
| **Prod profile** | `ddl-auto=validate`, `show-sql=false`, no devtools, tighter logging. Everything else inherits from `application.properties` env vars. | `application-prod.properties` (new) |
| **Secret hygiene** | The JWT secret & DB password currently live in git. Keep the `${…:default}` fallback for dev convenience but document that prod **must** override via env. Optionally add `.env.example`. | doc/README |
| **CORS wiring** | Confirm `CorsConfig` reads `app.cors.allowed-origins` (see §3.2 warning). | `CorsConfig.java` |
| **Endpoint/frontend sweep** | Cross-check every controller route against the frontend router (roadmap Phase 10 task "Frontend compatibility"). Produce a checklist; fix mismatches. | — |
| **Performance note** | GA is CPU-bound and already offloaded to the `gaExecutor` pool ([AsyncConfig.java](../src/main/java/com/timetablingapp/config/AsyncConfig.java), core=1/max=2). For large problems, size `JAVA_OPTS=-Xmx…` via the Docker env; consider read-only tx + fetch joins on the `TimetableService` query path to avoid N+1. | — |

**Add — [src/main/resources/application-prod.properties](../src/main/resources/application-prod.properties):**

```properties
# Production profile — all connection/secret values come from env vars (see application.properties).
spring.jpa.hibernate.ddl-auto=${DDL_AUTO:validate}
spring.jpa.show-sql=false
spring.jpa.open-in-view=false

logging.level.root=INFO
logging.level.com.timetablingapp=INFO
logging.level.org.hibernate.SQL=WARN

# Do not expose stack traces in error responses
server.error.include-stacktrace=never
server.error.include-message=never
```

---

## 7. Decisions to Confirm

### D1 — Test database strategy
**Recommendation:** three-tier (unit + web-slice DB-free by default; one opt-in Testcontainers full-stack test). This keeps `./gradlew build` runnable anywhere and isolates the real-DB dependency. **Alternative** rejected: making the whole suite require a running MySQL — brittle and slow, and blocks CI on infra.

### D2 — First real DB connection: now or later?
Phase 10 is the natural moment to make the **first real connection** and settle the `ddl-auto=validate` risk that every prior phase deferred. The Testcontainers test (§4.6) does this in a throwaway container using the Laravel dump — no risk to the real DB. **Recommend doing it now.** If you'd rather defer, the fast suite still passes; just mark §4.6 as pending and expect a column-fix round when the DB is finally wired.

### D3 — Testcontainers vs H2 for the light profile
H2 (MySQL mode) is used only so `@SpringBootTest`-style slice/context tests can wire without MySQL; it is **not** a validation of the real schema. If any entity refuses to map under H2, move that test to the Testcontainers tier rather than contorting the entity. (Most coverage is in `@WebMvcTest` slices, which need **no** datasource, so this rarely bites.)

### D4 — Schema seed source
The `laravel-schema.sql` must come from the real DB (`mysqldump --no-data`). If the DB is currently unreachable, generate it by running the Laravel migrations into a scratch MySQL and dumping that. This file is the single source of truth shared by compose and Testcontainers.

---

## 8. Verification Criteria

- [ ] `./gradlew clean build` passes **on a machine with no MySQL and no Docker** (fast suite is DB-free).
- [ ] `./gradlew test` runs unit + web-slice tests; all green; the `integration`-tagged test is skipped.
- [ ] `./gradlew integrationTest` (Docker present) spins a MySQL container, `ddl-auto=validate` **passes** against the Laravel schema, and the context boots — retiring the long-standing validate risk.
- [ ] Web-slice tests prove: 401 without auth, 200/`@WithMockUser`, and `@Valid` → 400 for at least Auth, Course, Room, Activity controllers.
- [ ] `docker build -t timetablingapp .` produces a runnable image (non-root, JRE 21).
- [ ] `docker-compose up` starts `db` (healthy) then `app`; the app connects and serves `/swagger-ui.html`.
- [ ] Full end-to-end against the compose stack: login → create data → `POST /api/timetable/generate` → `POST /api/timetable/save` → `GET /api/timetable/data` returns the schedule.
- [ ] No secrets required in code for prod: overriding `DB_*`, `JWT_SECRET`, `CORS_ORIGINS` via env fully reconfigures the app.

---

## 9. Suggested Build Order

1. **A** — `build.gradle.kts` deps/tasks, `application-test.properties`, fix `TimetablingappApplicationTests` (Task A). *Suite must at least compile & run empty.*
2. **B, unit tier** — `AuthServiceTest`, `SlotActivityServiceTest`. *No Spring — quickest wins.*
3. **B, web-slice tier** — `SecurityTestConfig`, then `AuthControllerTest`, `CourseControllerTest`, `RoomControllerTest`, `ActivityControllerTest`.
4. **C** — `Dockerfile`, `.dockerignore`, then generate `laravel-schema.sql`, then `docker-compose.yml`; verify `docker-compose up`.
5. **B, full-stack tier** — `TimetableServiceIntegrationTest` (needs the schema seed from step 4). Fix any `validate` failures here.
6. **D** — `application-prod.properties`, CORS wiring check, endpoint/frontend sweep, README secret note.

---

*End of Phase 10 plan. This completes the backend rewrite roadmap (Phases 1–10).*
