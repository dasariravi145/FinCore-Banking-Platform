package com.fincore.platform.account.controller;

import com.fincore.platform.account.dto.AccountResponse;
import com.fincore.platform.account.dto.UpdateAccountStatusRequest;
import com.fincore.platform.account.service.AccountService;
import com.fincore.platform.common.error.ApiErrorResponse;
import com.fincore.platform.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/accounts")
@PreAuthorize("hasRole('ADMIN')")
@SecurityRequirement(name = "BearerAuth")
@Tag(name = "Admin Account Operations", description = "Privileged account administration endpoints (freezing, unfreezing, closing)")
public class AdminAccountController {

    private final AccountService accountService;

    public AdminAccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @GetMapping
    @Operation(summary = "List all platform accounts", description = "Admin paginated view of all bank accounts")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Accounts returned"),
            @ApiResponse(responseCode = "403", description = "Forbidden: Caller lacks ROLE_ADMIN")
    })
    public ResponseEntity<Page<AccountResponse>> listAccounts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(page, Math.min(size, 100));
        return ResponseEntity.ok(accountService.getAllAccounts(pageable));
    }

    @PatchMapping("/{accountNumber}/status")
    @Operation(summary = "Freeze, unfreeze, or close account", description = "Administrative action to modify account operational status with audit tracking")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Account status updated",
                    content = @Content(schema = @Schema(implementation = AccountResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid status or missing reason",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden: Caller lacks ROLE_ADMIN",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Account not found",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<AccountResponse> updateAccountStatus(
            @PathVariable String accountNumber,
            @Valid @RequestBody UpdateAccountStatusRequest request) {

        String adminEmail = SecurityUtils.getCurrentUserEmail().orElse("ADMIN");
        AccountResponse response = accountService.updateAccountStatus(accountNumber, request, adminEmail);
        return ResponseEntity.ok(response);
    }
}

