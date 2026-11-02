package com.fincore.platform.user.controller;

import com.fincore.platform.auth.dto.UserProfileResponse;
import com.fincore.platform.common.error.ApiErrorResponse;
import com.fincore.platform.security.SecurityUtils;
import com.fincore.platform.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "User Management", description = "Endpoints for customer identity and profile management")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    @SecurityRequirement(name = "BearerAuth")
    @Operation(summary = "Get current authenticated customer details", description = "Returns identity attributes and role assignments for the current authenticated user")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Current user profile retrieved",
                    content = @Content(schema = @Schema(implementation = UserProfileResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized: missing or invalid JWT",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<UserProfileResponse> getCurrentUser() {
        UUID currentUserId = SecurityUtils.getCurrentUserId()
                .orElseThrow(() -> new IllegalStateException("SecurityContext did not contain authenticated user ID"));

        UserProfileResponse response = userService.getUserById(currentUserId);
        return ResponseEntity.ok(response);
    }
}

