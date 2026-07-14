package com.timetablingapp;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Boots the full context against a throwaway MySQL container seeded with the real schema, so
 * {@code spring.jpa.hibernate.ddl-auto=validate} (the production setting) is actually exercised.
 * Tagged "integration" — excluded from the default {@code test} task, run via
 * {@code ./gradlew integrationTest} (requires Docker). See phase10.md §3.4 Task A.
 */
@Tag("integration")
@Testcontainers
@SpringBootTest
class TimetablingappApplicationTests {

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("timetab")
            .withInitScript("schema/laravel-schema.sql");

    @Test
    void contextLoads() {
        // Verifies the full Spring context starts and ddl-auto=validate passes against the schema.
    }
}
