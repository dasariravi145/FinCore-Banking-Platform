package com.fincore.platform.customer.controller;

import com.fincore.platform.common.error.ApiErrorResponse;
import com.fincore.platform.customer.dto.CustomerResponse;
import com.fincore.platform.customer.service.CustomerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/customers")
@PreAuthorize("hasRole('ADMIN')")
@SecurityRequirement(name = "BearerAuth")
@Tag(name = "Admin Customer Management", description = "Privileged customer administration endpoints")
public class AdminCustomerController {

    private final CustomerService customerService;

    public AdminCustomerController(CustomerService customerService) {
        this.customerService = customerService;
    }

    @GetMapping
    @Operation(summary = "List all customer profiles", description = "Admin-only paginated listing of customer records")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Customer list returned"),
            @ApiResponse(responseCode = "403", description = "Forbidden: Caller lacks ROLE_ADMIN",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<Page<CustomerResponse>> listCustomers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(page, Math.min(size, 100));
        return ResponseEntity.ok(customerService.getAllCustomers(pageable));
    }

    @GetMapping("/{customerId}")
    @Operation(summary = "Get customer details by ID", description = "Admin-only customer profile inspection")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Customer details returned"),
            @ApiResponse(responseCode = "404", description = "Customer not found",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<CustomerResponse> getCustomer(@PathVariable UUID customerId) {
        return ResponseEntity.ok(customerService.getCustomerById(customerId));
    }
}

