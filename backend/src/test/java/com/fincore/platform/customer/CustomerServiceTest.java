package com.fincore.platform.customer;

import com.fincore.platform.common.audit.AuditEventType;
import com.fincore.platform.common.audit.SecurityAuditService;
import com.fincore.platform.common.error.ResourceNotFoundException;
import com.fincore.platform.common.error.UserAlreadyExistsException;
import com.fincore.platform.customer.dto.CreateCustomerRequest;
import com.fincore.platform.customer.dto.CustomerResponse;
import com.fincore.platform.customer.dto.UpdateCustomerRequest;
import com.fincore.platform.customer.entity.Customer;
import com.fincore.platform.customer.repository.CustomerRepository;
import com.fincore.platform.customer.service.CustomerService;
import com.fincore.platform.user.entity.Role;
import com.fincore.platform.user.entity.RoleName;
import com.fincore.platform.user.entity.User;
import com.fincore.platform.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CustomerServiceTest {

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private SecurityAuditService auditService;

    @InjectMocks
    private CustomerService customerService;

    private User user;
    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        user = new User("customer@fincore.local", "hashedPassword", "Aarav", "Sharma", "+919876543210");
        user.setId(userId);
        user.setRoles(Set.of(new Role(RoleName.ROLE_CUSTOMER, "Customer")));
    }

    @Test
    @DisplayName("Create customer profile successfully")
    void testCreateCustomerProfile_Success() {
        CreateCustomerRequest request = new CreateCustomerRequest(
                "Aarav", "Sharma", "+919876543210",
                LocalDate.of(1990, 5, 15),
                "123 Marine Drive", "Mumbai", "MH", "400020", "SYNTH-PAN-12345"
        );

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(customerRepository.existsByUserId(userId)).thenReturn(false);
        when(customerRepository.save(any(Customer.class))).thenAnswer(invocation -> {
            Customer c = invocation.getArgument(0);
            c.setId(UUID.randomUUID());
            return c;
        });

        CustomerResponse response = customerService.createCustomerProfile(userId, request);

        assertThat(response).isNotNull();
        assertThat(response.getFirstName()).isEqualTo("Aarav");
        assertThat(response.getLastName()).isEqualTo("Sharma");
        assertThat(response.getCustomerNumber()).startsWith("CUST-");
        assertThat(response.getStatus()).isEqualTo(com.fincore.platform.customer.entity.CustomerStatus.ACTIVE);

        verify(customerRepository, times(1)).save(any(Customer.class));
        verify(auditService, times(1)).recordEvent(
                eq(AuditEventType.CUSTOMER_PROFILE_CREATED),
                eq(user.getEmail()),
                any(), any(), eq("SUCCESS"), any()
        );
    }

    @Test
    @DisplayName("Create customer profile rejected when profile already exists")
    void testCreateCustomerProfile_DuplicateRejected() {
        CreateCustomerRequest request = new CreateCustomerRequest(
                "Aarav", "Sharma", "+919876543210",
                LocalDate.of(1990, 5, 15),
                "123 Marine Drive", "Mumbai", "MH", "400020", "SYNTH-PAN-12345"
        );

        when(customerRepository.existsByUserId(userId)).thenReturn(true);

        assertThatThrownBy(() -> customerService.createCustomerProfile(userId, request))
                .isInstanceOf(UserAlreadyExistsException.class)
                .hasMessageContaining("Customer profile already exists");

        verify(customerRepository, never()).save(any());
    }

    @Test
    @DisplayName("Update customer profile successfully")
    void testUpdateCustomerProfile_Success() {
        Customer existing = new Customer(user, "CUST-12345678", "Aarav", "Sharma", user.getEmail(), "+919876543210");

        UpdateCustomerRequest request = new UpdateCustomerRequest(
                "+919876543299", "456 New Blvd", "Pune", "MH", "411001"
        );

        when(customerRepository.findByUserId(userId)).thenReturn(Optional.of(existing));
        when(customerRepository.save(any(Customer.class))).thenAnswer(i -> i.getArgument(0));

        CustomerResponse response = customerService.updateCurrentCustomerProfile(userId, request);

        assertThat(response.getPhoneNumber()).isEqualTo("+919876543299");
        assertThat(response.getCity()).isEqualTo("Pune");
        assertThat(response.getAddressLine1()).isEqualTo("456 New Blvd");

        verify(auditService, times(1)).recordEvent(
                eq(AuditEventType.CUSTOMER_PROFILE_UPDATED),
                eq(user.getEmail()),
                any(), any(), eq("SUCCESS"), any()
        );
    }

    @Test
    @DisplayName("Get customer profile fails when profile not found")
    void testGetCustomerProfile_NotFound() {
        when(customerRepository.findByUserId(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> customerService.getCurrentCustomerProfile(userId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Customer profile not found");
    }
}
