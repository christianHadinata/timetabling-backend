plugins {
    java
    id("org.springframework.boot") version "4.0.2"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "com.timetablingapp"
version = "0.0.1-SNAPSHOT"
description = "Timetabling Backend - Spring Boot Migration"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

configurations {
    compileOnly {
        extendsFrom(configurations.annotationProcessor.get())
    }
}

repositories {
    mavenCentral()
}

dependencies {
    // Spring Boot Starters
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-validation")

    // JWT (jjwt)
    implementation("io.jsonwebtoken:jjwt-api:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.6")

    // API Documentation (Swagger UI)
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.8")

    // Excel I/O (Apache POI) — needed in later phases but declared now
    implementation("org.apache.poi:poi-ooxml:5.3.0")

    // JSON Processing
    implementation("com.google.code.gson:gson")

    // Text Utilities
    implementation("org.apache.commons:commons-text:1.15.0")

    // Lombok
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")

    // MySQL Driver
    runtimeOnly("com.mysql:mysql-connector-j")

    // Dev Tools
    developmentOnly("org.springframework.boot:spring-boot-devtools")

    // Testing
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    // Spring Boot 4.0 split @WebMvcTest out of spring-boot-test-autoconfigure into its own
    // starter — needed for the web-slice controller tests.
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.security:spring-security-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    // --- Phase 10: testing ---
    // H2 for the DB-free "test" profile (schema created from entities, not validated against MySQL)
    testRuntimeOnly("com.h2database:h2")

    // Testcontainers 2.x — real MySQL for the opt-in @Tag("integration") full-stack test.
    // Artifact IDs are "testcontainers-junit-jupiter" / "testcontainers-mysql" as of 2.0.x
    // (the pre-2.0 "junit-jupiter" / "mysql" coordinates topped out at 1.21.4 and don't exist
    // at the version the Spring Boot 4.0.2 BOM manages).
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testImplementation("org.testcontainers:testcontainers-mysql")
}

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
