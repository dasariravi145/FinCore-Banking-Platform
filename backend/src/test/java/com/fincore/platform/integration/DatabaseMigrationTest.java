package com.fincore.platform.integration;

import com.fincore.platform.user.entity.Role;
import com.fincore.platform.user.entity.RoleName;
import com.fincore.platform.user.repository.RoleRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@DisplayName("Task 7: Database Migration & Schema Correctness Tests")
class DatabaseMigrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private RoleRepository roleRepository;

    @Test
    @DisplayName("Test 13a: Flyway executes migrations and creates all required security tables")
    void testTablesExist() {
        List<String> tables = List.of(
                "roles",
                "permissions",
                "role_permissions",
                "users",
                "user_roles",
                "refresh_tokens",
                "security_audit_events"
        );

        for (String table : tables) {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.tables WHERE LOWER(table_name) = LOWER(?)",
                    Integer.class,
                    table
            );
            assertNotNull(count);
            assertTrue(count > 0, "Expected table '" + table + "' to exist after Flyway migration");
        }
    }

    @Test
    @DisplayName("Test 13b: Seed migration populated default banking roles and permissions")
    void testRolesSeeded() {
        Optional<Role> customerRole = roleRepository.findByName(RoleName.ROLE_CUSTOMER);
        assertTrue(customerRole.isPresent(), "ROLE_CUSTOMER must be present in database");
        assertFalse(customerRole.get().getPermissions().isEmpty(), "ROLE_CUSTOMER must have assigned permissions");

        Optional<Role> adminRole = roleRepository.findByName(RoleName.ROLE_ADMIN);
        assertTrue(adminRole.isPresent(), "ROLE_ADMIN must be present in database");
        assertTrue(adminRole.get().getPermissions().size() >= 4, "ROLE_ADMIN must have administrative permissions");
    }
}

