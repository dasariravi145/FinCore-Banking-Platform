package com.fincore.platform.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fincore.platform.account.dto.CreateAccountRequest;
import com.fincore.platform.account.entity.AccountType;
import com.fincore.platform.auth.dto.AuthResponse;
import com.fincore.platform.auth.dto.LoginRequest;
import com.fincore.platform.auth.dto.RegisterRequest;
import com.fincore.platform.customer.dto.CreateCustomerRequest;
import com.fincore.platform.fraud.dto.FraudReviewRequest;
import com.fincore.platform.fraud.entity.FraudAssessmentStatus;
import com.fincore.platform.transfer.dto.SimulatedFundingRequest;
import com.fincore.platform.transfer.dto.TransferRequest;
import com.fincore.platform.user.entity.Role;
import com.fincore.platform.user.entity.RoleName;
import com.fincore.platform.user.entity.User;
import com.fincore.platform.user.repository.RoleRepository;
import com.fincore.platform.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class FraudAndNotificationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private String adminToken;
    private String customerToken;
    private String sourceAccountNumber;
    private String destAccountNumber;

    @BeforeEach
    void setUp() throws Exception {
        Role adminRole = roleRepository.findByName(RoleName.ROLE_ADMIN).orElseThrow();
        User admin = new User("fraud.admin." + UUID.randomUUID() + "@fincore.local",
                passwordEncoder.encode("AdminPass123!"), "Fraud", "Admin", "+919876543333");
        admin.setRoles(Set.of(adminRole));
        admin = userRepository.save(admin);

        LoginRequest adminLogin = new LoginRequest(admin.getEmail(), "AdminPass123!");
        MvcResult adminLoginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(adminLogin)))
                .andExpect(status().isOk())
                .andReturn();
        adminToken = objectMapper.readValue(adminLoginResult.getResponse().getContentAsString(), AuthResponse.class).getAccessToken();

        // Customer
        String custEmail = "fraud.cust." + UUID.randomUUID() + "@fincore.local";
        RegisterRequest registerReq = new RegisterRequest("Rohit", "Sharma", custEmail, "CustPass123!", "+919876543334");
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerReq)))
                .andExpect(status().isCreated());

        LoginRequest custLogin = new LoginRequest(custEmail, "CustPass123!");
        MvcResult custLoginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(custLogin)))
                .andExpect(status().isOk())
                .andReturn();
        customerToken = objectMapper.readValue(custLoginResult.getResponse().getContentAsString(), AuthResponse.class).getAccessToken();

        // Profile
        CreateCustomerRequest profileReq = new CreateCustomerRequest(
                "Rohit", "Sharma", "+919876543334", null,
                "Wankhede Stadium", "Mumbai", "Maharashtra", "400020", "PAN-ROHIT"
        );
        mockMvc.perform(post("/api/v1/customers")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(profileReq)))
                .andExpect(status().isCreated());

        // Account 1: Source
        MvcResult acc1Result = mockMvc.perform(post("/api/v1/accounts")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateAccountRequest(AccountType.CURRENT))))
                .andExpect(status().isCreated())
                .andReturn();
        sourceAccountNumber = objectMapper.readTree(acc1Result.getResponse().getContentAsString()).get("accountNumber").asText();

        // Account 2: Destination
        MvcResult acc2Result = mockMvc.perform(post("/api/v1/accounts")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateAccountRequest(AccountType.SAVINGS))))
                .andExpect(status().isCreated())
                .andReturn();
        destAccountNumber = objectMapper.readTree(acc2Result.getResponse().getContentAsString()).get("accountNumber").asText();

        // Fund source account with ₹1,000,000.00
        SimulatedFundingRequest fundingReq = new SimulatedFundingRequest(sourceAccountNumber, BigDecimal.valueOf(1000000.00), "Initial capital");
        mockMvc.perform(post("/api/v1/admin/simulation/fund-account")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(fundingReq)))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("Fraud Detection: Transfer exceeding high value & depletion threshold is BLOCKED and leaves funds intact")
    void testFraudBlockedHighValueTransfer() throws Exception {
        // Attempt transfer of ₹980,000 (98% of 1,000,000 balance + > ₹500,000 threshold -> Score 75 -> BLOCK)
        TransferRequest blockedRequest = new TransferRequest(
                sourceAccountNumber, destAccountNumber, BigDecimal.valueOf(980000.00), "INR", "Suspicious transfer"
        );

        mockMvc.perform(post("/api/v1/transfers")
                        .header("Authorization", "Bearer " + customerToken)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(blockedRequest)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Transfer blocked by fraud detection rules")));

        // Verify source balance remains untouched at ₹1,000,000.00
        mockMvc.perform(get("/api/v1/accounts/" + sourceAccountNumber + "/balance")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availableBalance").value(1000000.00));

        // Verify Admin can see the blocked assessment in Admin Fraud Dashboard
        MvcResult assessmentsResult = mockMvc.perform(get("/api/v1/admin/fraud/assessments")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)))
                .andReturn();

        JsonNode assessmentsJson = objectMapper.readTree(assessmentsResult.getResponse().getContentAsString());
        String assessmentId = assessmentsJson.get("content").get(0).get("id").asText();

        // Admin reviews the assessment
        FraudReviewRequest reviewReq = new FraudReviewRequest(FraudAssessmentStatus.REJECTED, "Confirmed fraudulent intent with account holder");
        mockMvc.perform(post("/api/v1/admin/fraud/assessments/" + assessmentId + "/review")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reviewReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.reviewNotes").value("Confirmed fraudulent intent with account holder"));
    }
}
