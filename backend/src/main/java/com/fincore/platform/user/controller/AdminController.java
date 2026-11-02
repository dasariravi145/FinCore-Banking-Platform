package com.fincore.platform.user.controller;

import com.fincore.platform.auth.dto.UserProfileResponse;
import com.fincore.platform.common.audit.SecurityAuditEvent;
import com.fincore.platform.common.audit.SecurityAuditService;
import com.fincore.platform.common.error.ApiErrorResponse;
import com.fincore.platform.user.service.UserService;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('ADMIN')")
@SecurityRequirement(name = "BearerAuth")
@Tag(name = "Admin Operations", description = "Privileged endpoints restricted exclusively to ROLE_ADMIN")
public class AdminController {

    private final UserService userService;
    private final SecurityAuditService auditService;

    public AdminController(UserService userService, SecurityAuditService auditService) {
        this.userService = userService;
        this.auditService = auditService;
    }

    @GetMapping("/users")
    @Operation(summary = "List all registered platform users", description = "Restricted to administrators. Returns paginated user records.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "User list returned"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Forbidden: Caller lacks ROLE_ADMIN",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<Page<UserProfileResponse>> getUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(page, Math.min(size, 100));
        return ResponseEntity.ok(userService.getAllUsers(pageable));
    }

    @GetMapping("/audit-logs")
    @Operation(summary = "Query security audit trails", description = "Restricted to administrators. Returns chronological audit log records.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Audit trail returned"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Forbidden: Caller lacks ROLE_ADMIN",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<Page<SecurityAuditEvent>> getAuditLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(page, Math.min(size, 100));
        return ResponseEntity.ok(auditService.getRecentEvents(pageable));
    }
}

