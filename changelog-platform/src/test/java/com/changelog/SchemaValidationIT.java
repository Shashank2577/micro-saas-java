package com.changelog;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Boots the full Spring application against the local docker-compose Postgres
 * (started via {@code docker compose up -d postgres} on host port 5433).
 * A static initializer drops and recreates schemas {@code public} and
 * {@code cc} so Flyway re-applies all migrations from scratch on every run.
 * Hibernate's {@code ddl-auto: validate} then compares every {@code @Entity}
 * against the resulting schema.
 *
 * <p>If this test passes, every entity-to-column type mapping is consistent
 * with the migrations. It catches the kind of drift we hit during manual
 * boot: missing columns, wrong column types ({@code TEXT[]} vs {@code JSONB},
 * {@code INT} vs {@code VARCHAR}), missing JSONB type annotations, etc.
 *
 * <p><b>Why not Testcontainers?</b> The bundled {@code docker-java} client
 * is incompatible with Docker Desktop 4.55+ (Engine 29.x, API 1.52) on this
 * machine — it returns HTTP 400 on {@code /info}. Until that is resolved
 * upstream, this test relies on the already-required dev Postgres.
 */
@SpringBootTest(
        classes = ChangelogPlatformApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("local") // Excludes SecurityConfig (@Profile("!local")) so OAuth2 isn't required
@EnableAutoConfiguration(exclude = OAuth2ResourceServerAutoConfiguration.class)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:postgresql://localhost:5433/changelog",
        "spring.datasource.username=changelog",
        "spring.datasource.password=changelog",
        "spring.jpa.hibernate.ddl-auto=validate"
})
class SchemaValidationIT {

    private static final String JDBC_URL = "jdbc:postgresql://localhost:5433/changelog";
    private static final String DB_USER = "changelog";
    private static final String DB_PASS = "changelog";

    static {
        // Runs when JUnit loads the class — before the Spring TestContext is
        // built — so Flyway sees a pristine database on every test run.
        try (Connection c = DriverManager.getConnection(JDBC_URL, DB_USER, DB_PASS);
             Statement s = c.createStatement()) {
            s.execute("DROP SCHEMA IF EXISTS public CASCADE");
            s.execute("CREATE SCHEMA public");
            s.execute("DROP SCHEMA IF EXISTS cc CASCADE");
        } catch (SQLException e) {
            throw new IllegalStateException(
                    "Could not reset test schemas. Is `docker compose up -d postgres` running?", e);
        }
    }

    @Test
    void contextLoadsAndSchemaValidates() {
        // Empty body: success means Flyway applied every migration AND
        // Hibernate validated every @Entity against the resulting schema.
    }
}
