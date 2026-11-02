package com.fincore.platform.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Task 7: Actuator Health & Observability Tests")
class HealthEndpointTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("Test 14a: Public health endpoint returns 200 OK with UP status")
    void testHealthEndpointReturnsUp() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    @DisplayName("Test 14b: Public info endpoint is accessible")
    void testInfoEndpointIsAccessible() throws Exception {
        mockMvc.perform(get("/actuator/info"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Test 14c: Sensitive actuator endpoints are secured or unexposed")
    void testSensitiveActuatorEndpointsSecured() throws Exception {
        // Unexposed or restricted endpoints must not return open 200 OK without admin auth
        mockMvc.perform(get("/actuator/env"))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    org.junit.jupiter.api.Assertions.assertTrue(status == 401 || status == 404,
                            "Sensitive actuator endpoint /actuator/env must be secured (401) or unexposed (404), got: " + status);
                });
    }
}

