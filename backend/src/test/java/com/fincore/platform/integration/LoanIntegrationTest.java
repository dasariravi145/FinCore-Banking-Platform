package com.fincore.platform.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fincore.platform.account.dto.CreateAccountRequest;
import com.fincore.platform.account.entity.AccountType;
import com.fincore.platform.auth.dto.AuthResponse;
import com.fincore.platform.auth.dto.LoginRequest;
import com.fincore.platform.auth.dto.RegisterRequest;
import com.fincore.platform.customer.dto.CreateCustomerRequest;
import com.fincore.platform.loan.dto.CalculateEmiRequest;
import com.fincore.platform.loan.dto.LoanApplicationRequest;
import com.fincore.platform.loan.dto.LoanApprovalRequest;
import com.fincore.platform.loan.dto.LoanRepaymentRequest;
import com.fincore.platform.loan.entity.LoanType;
import com.fincore.platform.transfer.dto.SimulatedFundingRequest;
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
class LoanIntegrationTest {

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

    @BeforeEach
    void setUp() throws Exception {
        // 1. Seed Admin user
        Role adminRole = roleRepository.findByName(RoleName.ROLE_ADMIN).orElseThrow();
        User admin = new User("loan.admin." + UUID.randomUUID() + "@fincore.local",
                passwordEncoder.encode("AdminPass123!"), "Loan", "Admin", "+919876543290");
        admin.setRoles(Set.of(adminRole));
        admin = userRepository.save(admin);

        // Authenticate Admin
        LoginRequest adminLogin = new LoginRequest(admin.getEmail(), "AdminPass123!");
        MvcResult adminLoginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(adminLogin)))
                .andExpect(status().isOk())
                .andReturn();
        AuthResponse adminAuth = objectMapper.readValue(adminLoginResult.getResponse().getContentAsString(), AuthResponse.class);
        adminToken = adminAuth.getAccessToken();

        // 2. Register & Authenticate Customer
        String customerEmail = "loan.cust." + UUID.randomUUID() + "@fincore.local";
        RegisterRequest registerReq = new RegisterRequest(
                "Suresh", "Raina", customerEmail, "CustPass123!", "+919876543291"
        );
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerReq)))
                .andExpect(status().isCreated());

        LoginRequest custLogin = new LoginRequest(customerEmail, "CustPass123!");
        MvcResult custLoginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(custLogin)))
                .andExpect(status().isOk())
                .andReturn();
        AuthResponse custAuth = objectMapper.readValue(custLoginResult.getResponse().getContentAsString(), AuthResponse.class);
        customerToken = custAuth.getAccessToken();
    }

    @Test
    @DisplayName("End-to-End Loan Flow: Quote -> Application -> Approval -> Disbursement -> Repayment -> Notifications")
    void testEndToEndLoanLifecycle() throws Exception {
        // Step 1: Calculate EMI Quote (Public endpoint)
        CalculateEmiRequest quoteReq = new CalculateEmiRequest(
                BigDecimal.valueOf(100000.00), BigDecimal.valueOf(12.0), 12
        );
        mockMvc.perform(post("/api/v1/loans/calculate-emi")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(quoteReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.monthlyEmi").value(8884.88));

        // Step 2: Create Customer Profile
        CreateCustomerRequest profileReq = new CreateCustomerRequest(
                "Suresh", "Raina", "+919876543291", null,
                "10 MG Road", "Bangalore", "Karnataka", "560001", "PAN-123456"
        );
        mockMvc.perform(post("/api/v1/customers")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(profileReq)))
                .andExpect(status().isCreated());

        // Step 3: Open Savings Bank Account
        CreateAccountRequest accountReq = new CreateAccountRequest(AccountType.SAVINGS);
        MvcResult accountResult = mockMvc.perform(post("/api/v1/accounts")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(accountReq)))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode accountJson = objectMapper.readTree(accountResult.getResponse().getContentAsString());
        String accountNumber = accountJson.get("accountNumber").asText();

        // Step 4: Apply for Loan
        LoanApplicationRequest loanReq = new LoanApplicationRequest(
                LoanType.PERSONAL, BigDecimal.valueOf(100000.00), 12, accountNumber
        );
        MvcResult loanResult = mockMvc.perform(post("/api/v1/loans/apply")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loanReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("APPLIED"))
                .andExpect(jsonPath("$.schedules.length()").value(12))
                .andReturn();

        JsonNode loanJson = objectMapper.readTree(loanResult.getResponse().getContentAsString());
        String loanNumber = loanJson.get("loanNumber").asText();

        // Step 5: Admin Approves Loan
        LoanApprovalRequest approvalReq = new LoanApprovalRequest(true, null);
        mockMvc.perform(post("/api/v1/admin/loans/" + loanNumber + "/review")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(approvalReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        // Step 6: Admin Disburses Loan
        mockMvc.perform(post("/api/v1/admin/loans/" + loanNumber + "/disburse")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        // Verify customer account balance increased by ₹100,000.00
        mockMvc.perform(get("/api/v1/accounts/" + accountNumber + "/balance")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availableBalance").value(100000.00));

        // Step 7: Customer repays first installment (₹8,884.88)
        LoanRepaymentRequest repayReq = new LoanRepaymentRequest(
                BigDecimal.valueOf(8884.88), accountNumber, "Installment 1"
        );
        mockMvc.perform(post("/api/v1/loans/" + loanNumber + "/repay")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(repayReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalPaid").value(8884.88))
                .andExpect(jsonPath("$.interestPaid").isNumber())
                .andExpect(jsonPath("$.principalPaid").isNumber())
                .andExpect(jsonPath("$.loanStatus").value("ACTIVE"));

        // Step 8: Check Notifications Inbox
        mockMvc.perform(get("/api/v1/notifications")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").value(org.hamcrest.Matchers.greaterThanOrEqualTo(3)));

        // Check Unread Count
        mockMvc.perform(get("/api/v1/notifications/unread-count")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount").value(org.hamcrest.Matchers.greaterThan(0)));
    }
}
