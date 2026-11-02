package com.fincore.platform.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Task 7: Role-Based Authorization & Endpoint Access Control Tests")
class SecurityAuthorizationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("Test 7: Unauthenticated access to admin endpoints triggers 401 Unauthorized")
    void testUnauthenticatedAccessDenied() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"));
    }

    @Test
    @WithMockUser(username = "customer@fincore.local", roles = {"CUSTOMER"})
    @DisplayName("Test 8: CUSTOMER role access to admin user endpoint is strictly forbidden (403)")
    void testCustomerForbiddenOnAdminEndpoint() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("Forbidden"))
                .andExpect(jsonPath("$.message").value("Access denied: insufficient permissions to access this banking resource"));
    }

    @Test
    @WithMockUser(username = "customer@fincore.local", roles = {"CUSTOMER"})
    @DisplayName("Test 8b: CUSTOMER role access to admin audit-logs endpoint is strictly forbidden (403)")
    void testCustomerForbiddenOnAuditLogsEndpoint() throws Exception {
        mockMvc.perform(get("/api/v1/admin/audit-logs"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    @WithMockUser(username = "admin@fincore.local", roles = {"ADMIN"})
    @DisplayName("Test 9: ADMIN role access to admin endpoints succeeds (200)")
    void testAdminAuthorizedOnAdminEndpoints() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/admin/audit-logs"))
                .andExpect(status().isOk());
    }
}
