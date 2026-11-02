package com.fincore.platform.auth.dto;

import com.fincore.platform.user.entity.Permission;
import com.fincore.platform.user.entity.Role;
import com.fincore.platform.user.entity.User;
import com.fincore.platform.user.entity.UserStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Schema(description = "User profile details response")
public class UserProfileResponse {

    @Schema(description = "User unique UUID identifier", example = "a5e8f492-36c1-482a-9cb0-1ebc90e6b541")
    private UUID id;

    @Schema(description = "Registered email address", example = "jane.doe@example.com")
    private String email;

    @Schema(description = "Customer legal first name", example = "Jane")
    private String firstName;

    @Schema(description = "Customer legal last name", example = "Doe")
    private String lastName;

    @Schema(description = "Contact phone number", example = "+14155552671")
    private String phoneNumber;

    @Schema(description = "User account lifecycle status", example = "ACTIVE")
    private UserStatus status;

    @Schema(description = "Assigned security roles", example = "[\"ROLE_CUSTOMER\"]")
    private Set<String> roles;

    @Schema(description = "Effective permissions", example = "[\"USER_READ\", \"USER_WRITE\"]")
    private Set<String> permissions;

    @Schema(description = "Profile creation timestamp", example = "2026-11-01T16:00:00Z")
    private Instant createdAt;

    public UserProfileResponse() {}

    public static UserProfileResponse fromUser(User user) {
        UserProfileResponse response = new UserProfileResponse();
        response.setId(user.getId());
        response.setEmail(user.getEmail());
        response.setFirstName(user.getFirstName());
        response.setLastName(user.getLastName());
        response.setPhoneNumber(user.getPhoneNumber());
        response.setStatus(user.getStatus());

        Set<String> roleNames = user.getRoles().stream()
                .map(r -> r.getName().name())
                .collect(Collectors.toSet());
        response.setRoles(roleNames);

        Set<String> permissionNames = user.getRoles().stream()
                .flatMap(r -> r.getPermissions().stream())
                .map(Permission::getName)
                .collect(Collectors.toSet());
        response.setPermissions(permissionNames);

        response.setCreatedAt(user.getCreatedAt());
        return response;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public UserStatus getStatus() {
        return status;
    }

    public void setStatus(UserStatus status) {
        this.status = status;
    }

    public Set<String> getRoles() {
        return roles;
    }

    public void setRoles(Set<String> roles) {
        this.roles = roles;
    }

    public Set<String> getPermissions() {
        return permissions;
    }

    public void setPermissions(Set<String> permissions) {
        this.permissions = permissions;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}

