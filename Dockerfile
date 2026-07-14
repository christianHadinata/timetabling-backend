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
