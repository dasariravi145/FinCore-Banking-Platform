package com.fincore.platform.customer.controller;

import com.fincore.platform.common.error.ApiErrorResponse;
import com.fincore.platform.customer.dto.CreateCustomerRequest;
import com.fincore.platform.customer.dto.CustomerResponse;
import com.fincore.platform.customer.dto.UpdateCustomerRequest;
import com.fincore.platform.customer.service.CustomerService;
import com.fincore.platform.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/customers")
@PreAuthorize("isAuthenticated()")
@SecurityRequirement(name = "BearerAuth")
@Tag(name = "Customer Self-Service", description = "Endpoints for managing the authenticated customer's banking profile")
public class CustomerController {

    private final CustomerService customerService;

    public CustomerController(CustomerService customerService) {
        this.customerService = customerService;
    }

    @PostMapping
    @Operation(summary = "Create customer profile for authenticated user", description = "Associates legal customer identity attributes with authenticated user credentials")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Customer profile created successfully",
                    content = @Content(schema = @Schema(implementation = CustomerResponse.class))),
            @ApiResponse(responseCode = "400", description = "Validation failed on input attributes",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Customer profile already exists for this user",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<CustomerResponse> createCustomer(@Valid @RequestBody CreateCustomerRequest request) {
        UUID currentUserId = SecurityUtils.getCurrentUserId()
                .orElseThrow(() -> new IllegalStateException("SecurityContext did not contain authenticated user ID"));

        CustomerResponse response = customerService.createCustomerProfile(currentUserId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/me")
    @Operation(summary = "Get current authenticated customer profile", description = "Returns customer profile of the authenticated caller")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Customer profile returned",
                    content = @Content(schema = @Schema(implementation = CustomerResponse.class))),
            @ApiResponse(responseCode = "404", description = "Customer profile not found for user",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<CustomerResponse> getMyProfile() {
        UUID currentUserId = SecurityUtils.getCurrentUserId()
                .orElseThrow(() -> new IllegalStateException("SecurityContext did not contain authenticated user ID"));

        CustomerResponse response = customerService.getCurrentCustomerProfile(currentUserId);
        return ResponseEntity.ok(response);
    }

    @PutMapping({"", "/me"})
    @Operation(summary = "Update current authenticated customer contact/address", description = "Updates address and contact details of caller")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Customer profile updated",
                    content = @Content(schema = @Schema(implementation = CustomerResponse.class))),
            @ApiResponse(responseCode = "400", description = "Validation error",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Customer profile not found",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<CustomerResponse> updateMyProfile(@Valid @RequestBody UpdateCustomerRequest request) {
        UUID currentUserId = SecurityUtils.getCurrentUserId()
                .orElseThrow(() -> new IllegalStateException("SecurityContext did not contain authenticated user ID"));

        CustomerResponse response = customerService.updateCurrentCustomerProfile(currentUserId, request);
        return ResponseEntity.ok(response);
    }
}

