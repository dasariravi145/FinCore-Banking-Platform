package com.fincore.platform.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fincore.platform.auth.dto.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Task 7: End-to-End Authentication & Session Lifecycle Integration Tests")
class AuthIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("Comprehensive Lifecycle: Register -> Login -> Profile -> Refresh -> Replay Detect -> Logout")
    void testCompleteAuthenticationLifecycle() throws Exception {
        String email = "integrations." + System.currentTimeMillis() + "@fincore.local";
        String password = "SecureBankingPass123!";

        // 1. Register new customer
        RegisterRequest registerReq = new RegisterRequest(
                "Jonathan", "Archer", email, password, "+14155550199"
        );

        MvcResult registerResult = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.user.email").value(email))
                .andExpect(jsonPath("$.user.roles", hasItem("ROLE_CUSTOMER")))
                .andReturn();

        AuthResponse initialAuth = objectMapper.readValue(
                registerResult.getResponse().getContentAsString(), AuthResponse.class);

        // 2. Reject Duplicate Registration
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerReq)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message", containsString("already exists")));

        // 3. Login with Correct Credentials
        LoginRequest loginReq = new LoginRequest(email, password);
        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andReturn();

        AuthResponse loginAuth = objectMapper.readValue(
                loginResult.getResponse().getContentAsString(), AuthResponse.class);
        String activeAccessToken = loginAuth.getAccessToken();
        String activeRefreshToken = loginAuth.getRefreshToken();

        // 4. Reject Login with Bad Password
        LoginRequest badLogin = new LoginRequest(email, "WrongPassword123!");
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(badLogin)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email or password"));

        // 5. Access Protected Profile using Access Token
        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + activeAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.roles", hasItem("ROLE_CUSTOMER")));

        mockMvc.perform(get("/api/v1/users/me")
                        .header("Authorization", "Bearer " + activeAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email));

        // 6. Rotate Refresh Token
        RefreshTokenRequest refreshReq = new RefreshTokenRequest(activeRefreshToken);
        MvcResult refreshResult = mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(refreshReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andReturn();

        TokenRefreshResponse rotatedAuth = objectMapper.readValue(
                refreshResult.getResponse().getContentAsString(), TokenRefreshResponse.class);
        String rotatedAccessToken = rotatedAuth.getAccessToken();
        String rotatedRefreshToken = rotatedAuth.getRefreshToken();

        assertNotEquals(activeRefreshToken, rotatedRefreshToken, "Rotated refresh token must be brand new");

        // 7. Verify new access token works on protected endpoints
        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + rotatedAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email));

        // 8. Replay Attack Detection: Attempt to reuse the PREVIOUS (now revoked) refresh token
        RefreshTokenRequest replayReq = new RefreshTokenRequest(activeRefreshToken);
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(replayReq)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message", containsString("compromise detected")));

        // 9. Session Revocation: After compromise protocol was triggered, even the rotated token was revoked
        RefreshTokenRequest compromisedReq = new RefreshTokenRequest(rotatedRefreshToken);
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(compromisedReq)))
                .andExpect(status().isUnauthorized());

        // 10. Login again and test clean Logout
        MvcResult freshLoginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isOk())
                .andReturn();

        AuthResponse freshAuth = objectMapper.readValue(
                freshLoginResult.getResponse().getContentAsString(), AuthResponse.class);

        // Perform clean Logout
        LogoutRequest logoutReq = new LogoutRequest(freshAuth.getRefreshToken());
        mockMvc.perform(post("/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(logoutReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message", containsString("Logged out successfully")));

        // Refreshing after logout must be rejected
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshTokenRequest(freshAuth.getRefreshToken()))))
                .andExpect(status().isUnauthorized());
    }
}

