package com.fincore.platform.integration;

import com.fincore.platform.user.entity.Role;
import com.fincore.platform.user.entity.RoleName;
import com.fincore.platform.user.repository.RoleRepository;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("Task 7: Testcontainers Isolated PostgreSQL Integration Test")
class PostgresTestcontainersIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("fincore_test_tc")
            .withUsername("fincore_tc")
            .withPassword("fincore_tc_pass");

    @BeforeAll
    static void checkDockerEnvironment() {
        boolean dockerAvailable = false;
        try {
            dockerAvailable = DockerClientFactory.instance().isDockerAvailable();
        } catch (Throwable ignored) {}
        Assumptions.assumeTrue(dockerAvailable, "Docker daemon is not available on this host; skipping Testcontainers execution.");
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        if (postgres.isRunning()) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl);
            registry.add("spring.datasource.username", postgres::getUsername);
            registry.add("spring.datasource.password", postgres::getPassword);
            registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
            registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.PostgreSQLDialect");
        }
    }

    @Autowired
    private RoleRepository roleRepository;

    @Test
    @DisplayName("Verify Flyway migrations against isolated PostgreSQL Testcontainer")
    void testPostgresMigration() {
        Optional<Role> customerRole = roleRepository.findByName(RoleName.ROLE_CUSTOMER);
        assertTrue(customerRole.isPresent(), "ROLE_CUSTOMER must be migrated into containerized PostgreSQL");
    }
}

