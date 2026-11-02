package com.fincore.platform.customer.service;

import com.fincore.platform.common.audit.AuditEventType;
import com.fincore.platform.common.audit.SecurityAuditService;
import com.fincore.platform.common.error.ResourceNotFoundException;
import com.fincore.platform.common.error.UserAlreadyExistsException;
import com.fincore.platform.customer.dto.CreateCustomerRequest;
import com.fincore.platform.customer.dto.CustomerResponse;
import com.fincore.platform.customer.dto.UpdateCustomerRequest;
import com.fincore.platform.customer.entity.Customer;
import com.fincore.platform.customer.entity.CustomerStatus;
import com.fincore.platform.customer.repository.CustomerRepository;
import com.fincore.platform.user.entity.User;
import com.fincore.platform.user.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.UUID;

@Service
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final UserRepository userRepository;
    private final SecurityAuditService auditService;
    private final SecureRandom secureRandom = new SecureRandom();

    public CustomerService(CustomerRepository customerRepository,
                           UserRepository userRepository,
                           SecurityAuditService auditService) {
        this.customerRepository = customerRepository;
        this.userRepository = userRepository;
        this.auditService = auditService;
    }

    @Transactional
    public CustomerResponse createCustomerProfile(UUID userId, CreateCustomerRequest request) {
        if (customerRepository.existsByUserId(userId)) {
            throw new UserAlreadyExistsException("Customer profile already exists for authenticated user");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + userId));

        String customerNumber = generateUniqueCustomerNumber();

        Customer customer = new Customer(
                user,
                customerNumber,
                request.getFirstName().trim(),
                request.getLastName().trim(),
                user.getEmail(),
                request.getPhoneNumber() != null ? request.getPhoneNumber().trim() : user.getPhoneNumber()
        );
        customer.setDateOfBirth(request.getDateOfBirth());
        customer.setAddressLine1(request.getAddressLine1());
        customer.setAddressLine2(request.getAddressLine2());
        customer.setCity(request.getCity());
        customer.setState(request.getState());
        customer.setPostalCode(request.getPostalCode());
        if (request.getCountry() != null) {
            customer.setCountry(request.getCountry());
        }
        customer.setNationalId(request.getNationalId());
        customer.setStatus(CustomerStatus.ACTIVE);

        Customer saved = customerRepository.save(customer);

        auditService.recordEvent(
                AuditEventType.CUSTOMER_PROFILE_CREATED,
                user.getEmail(),
                null,
                null,
                "SUCCESS",
                "Created Customer profile: " + customerNumber + " for User: " + user.getId()
        );

        return CustomerResponse.fromEntity(saved);
    }

    @Transactional(readOnly = true)
    public CustomerResponse getCurrentCustomerProfile(UUID userId) {
        Customer customer = getCustomerEntityByUserId(userId);
        return CustomerResponse.fromEntity(customer);
    }

    @Transactional
    public CustomerResponse updateCurrentCustomerProfile(UUID userId, UpdateCustomerRequest request) {
        Customer customer = getCustomerEntityByUserId(userId);

        if (request.getPhoneNumber() != null && !request.getPhoneNumber().isBlank()) {
            customer.setPhoneNumber(request.getPhoneNumber().trim());
        }
        if (request.getAddressLine1() != null) {
            customer.setAddressLine1(request.getAddressLine1().trim());
        }
        if (request.getAddressLine2() != null) {
            customer.setAddressLine2(request.getAddressLine2().trim());
        }
        if (request.getCity() != null) {
            customer.setCity(request.getCity().trim());
        }
        if (request.getState() != null) {
            customer.setState(request.getState().trim());
        }
        if (request.getPostalCode() != null) {
            customer.setPostalCode(request.getPostalCode().trim());
        }
        if (request.getCountry() != null) {
            customer.setCountry(request.getCountry().trim());
        }

        Customer saved = customerRepository.save(customer);

        auditService.recordEvent(
                AuditEventType.CUSTOMER_PROFILE_UPDATED,
                customer.getEmail(),
                null,
                null,
                "SUCCESS",
                "Updated contact/address for Customer: " + customer.getCustomerNumber()
        );

        return CustomerResponse.fromEntity(saved);
    }

    @Transactional(readOnly = true)
    public Customer getCustomerEntityByUserId(UUID userId) {
        return customerRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer profile not found for user: " + userId));
    }

    @Transactional(readOnly = true)
    public CustomerResponse getCustomerById(UUID customerId) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found with ID: " + customerId));
        return CustomerResponse.fromEntity(customer);
    }

    @Transactional(readOnly = true)
    public Page<CustomerResponse> getAllCustomers(Pageable pageable) {
        return customerRepository.findAll(pageable)
                .map(CustomerResponse::fromEntity);
    }

    private String generateUniqueCustomerNumber() {
        String number;
        do {
            int randomVal = 10000000 + secureRandom.nextInt(90000000);
            number = "CUST-" + randomVal;
        } while (customerRepository.existsByCustomerNumber(number));
        return number;
    }
}

