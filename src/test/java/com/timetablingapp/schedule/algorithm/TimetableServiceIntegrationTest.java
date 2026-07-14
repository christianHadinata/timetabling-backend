package com.timetablingapp.schedule.algorithm;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * The only DB-backed test in the suite. Boots the real context against a throwaway MySQL
 * container seeded with the schema, so {@code ddl-auto=validate} is actually exercised against
 * a real MySQL instance instead of the H2 stand-in used by the web-slice/unit tiers.
 * Tagged "integration" — excluded from {@code ./gradlew test}, run via
 * {@code ./gradlew integrationTest} (requires Docker). See phase10.md §4.6.
 */
@Tag("integration")
@Testcontainers
@SpringBootTest
class TimetableServiceIntegrationTest {

    @Container
    @ServiceConnection   // auto-wires spring.datasource.* to the container — no manual URL plumbing
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("timetab")
            .withInitScript("schema/laravel-schema.sql");

    @Autowired TimetableService timetableService;

    @Test
    void contextBootsAndValidatesAgainstRealSchema() {
        // If this test's context loads, ddl-auto=validate passed against the real schema — the
        // single most important unproven risk carried since Phase 1.
        Assertions.assertNotNull(timetableService);
    }
}
