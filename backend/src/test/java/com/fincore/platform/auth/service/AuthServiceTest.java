package com.fincore.platform.auth.service;

import com.fincore.platform.auth.dto.AuthResponse;
import com.fincore.platform.auth.dto.LoginRequest;
import com.fincore.platform.auth.dto.RegisterRequest;
import com.fincore.platform.common.audit.AuditEventType;
import com.fincore.platform.common.audit.SecurityAuditService;
import com.fincore.platform.common.error.UserAlreadyExistsException;
import com.fincore.platform.security.JwtTokenProvider;
import com.fincore.platform.security.UserPrincipal;
import com.fincore.platform.user.entity.Role;
import com.fincore.platform.user.entity.RoleName;
import com.fincore.platform.user.entity.User;
import com.fincore.platform.user.entity.UserStatus;
import com.fincore.platform.user.repository.RoleRepository;
import com.fincore.platform.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Task 7: Authentication & Registration Business Logic Tests")
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private JwtTokenProvider tokenProvider;

    @Mock
    private RefreshTokenService refreshTokenService;

    @Mock
    private SecurityAuditService auditService;

    private PasswordEncoder passwordEncoder;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder(12);
        authService = new AuthService(
                userRepository,
                roleRepository,
                passwordEncoder,
                tokenProvider,
                refreshTokenService,
                auditService
        );
    }

    @Test
    @DisplayName("Test 1 & 4: Successful registration enforces password hashing and assigns ROLE_CUSTOMER")
    void testSuccessfulRegistration() {
        RegisterRequest request = new RegisterRequest(
                "Bruce", "Wayne", "bruce@wayne-enterprises.com", "GothamKnight2026!", "+15551234567"
        );

        when(userRepository.existsByEmailIgnoreCase(anyString())).thenReturn(false);

        Role customerRole = new Role(RoleName.ROLE_CUSTOMER, "Customer Role");
        customerRole.setId(1L);
        when(roleRepository.findByName(RoleName.ROLE_CUSTOMER)).thenReturn(Optional.of(customerRole));

        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User u = invocation.getArgument(0);
            u.setId(UUID.randomUUID());
            return u;
        });

        when(tokenProvider.generateToken(any(UserPrincipal.class))).thenReturn("mock-jwt-token");
        when(tokenProvider.getAccessTokenExpirationMs()).thenReturn(900000L);
        when(refreshTokenService.createRefreshToken(any(User.class), anyString(), anyString())).thenReturn("mock-refresh-token");

        AuthResponse response = authService.register(request, "127.0.0.1", "Chrome");

        assertNotNull(response);
        assertEquals("mock-jwt-token", response.getAccessToken());
        assertEquals("mock-refresh-token", response.getRefreshToken());

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());

        User savedUser = userCaptor.getValue();
        assertEquals("bruce@wayne-enterprises.com", savedUser.getEmail());
        assertNotEquals("GothamKnight2026!", savedUser.getPasswordHash(), "Plaintext password must never be stored");
        assertTrue(passwordEncoder.matches("GothamKnight2026!", savedUser.getPasswordHash()), "Password must be BCrypt hashed");

        // Verify strictly assigned ROLE_CUSTOMER
        assertEquals(1, savedUser.getRoles().size());
        assertTrue(savedUser.getRoles().stream().anyMatch(r -> r.getName() == RoleName.ROLE_CUSTOMER));

        verify(auditService).recordEvent(
                eq(AuditEventType.REGISTRATION_SUCCESS),
                eq("bruce@wayne-enterprises.com"),
                eq("127.0.0.1"),
                eq("Chrome"),
                eq("SUCCESS"),
                anyString()
        );
    }

    @Test
    @DisplayName("Test 2: Duplicate email registration is rejected with Conflict exception")
    void testDuplicateEmailRejection() {
        RegisterRequest request = new RegisterRequest(
                "Bruce", "Wayne", "duplicate@example.com", "GothamKnight2026!", "+15551234567"
        );

        when(userRepository.existsByEmailIgnoreCase("duplicate@example.com")).thenReturn(true);

        UserAlreadyExistsException ex = assertThrows(UserAlreadyExistsException.class, () ->
                authService.register(request, "127.0.0.1", "Chrome"));

        assertTrue(ex.getMessage().contains("already exists"));

        verify(auditService).recordEvent(
                eq(AuditEventType.REGISTRATION_FAILURE),
                eq("duplicate@example.com"),
                eq("127.0.0.1"),
                eq("Chrome"),
                eq("FAILURE"),
                anyString()
        );
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("Test 5: Successful login returns token pair and resets failed login attempts")
    void testSuccessfulLogin() {
        String rawPassword = "StrongPassword123!";
        String encodedPassword = passwordEncoder.encode(rawPassword);

        User user = new User("clark@dailyplanet.com", encodedPassword, "Clark", "Kent", "+15559876543");
        user.setId(UUID.randomUUID());
        user.setFailedLoginAttempts(2); // previous failed attempts

        Role customerRole = new Role(RoleName.ROLE_CUSTOMER, "Customer Role");
        user.setRoles(Collections.singleton(customerRole));

        when(userRepository.findByEmailIgnoreCase("clark@dailyplanet.com")).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenReturn(user);
        when(tokenProvider.generateToken(any(UserPrincipal.class))).thenReturn("access-jwt-token");
        when(tokenProvider.getAccessTokenExpirationMs()).thenReturn(900000L);
        when(refreshTokenService.createRefreshToken(any(User.class), anyString(), anyString())).thenReturn("new-refresh-token");

        LoginRequest request = new LoginRequest("clark@dailyplanet.com", rawPassword);
        AuthResponse response = authService.login(request, "127.0.0.1", "Firefox");

        assertNotNull(response);
        assertEquals("access-jwt-token", response.getAccessToken());
        assertEquals("new-refresh-token", response.getRefreshToken());
        assertEquals(0, user.getFailedLoginAttempts(), "Failed login attempts must be reset on success");

        verify(auditService).recordEvent(
                eq(AuditEventType.LOGIN_SUCCESS),
                eq("clark@dailyplanet.com"),
                eq("127.0.0.1"),
                eq("Firefox"),
                eq("SUCCESS"),
                anyString()
        );
    }

    @Test
    @DisplayName("Test 6: Incorrect password rejection increments failed attempts and protects against enumeration")
    void testIncorrectPasswordRejection() {
        String encodedPassword = passwordEncoder.encode("CorrectPassword123!");
        User user = new User("diana@themyscira.com", encodedPassword, "Diana", "Prince", "+15550001111");
        user.setId(UUID.randomUUID());
        user.setFailedLoginAttempts(0);

        when(userRepository.findByEmailIgnoreCase("diana@themyscira.com")).thenReturn(Optional.of(user));

        LoginRequest request = new LoginRequest("diana@themyscira.com", "WrongPassword999!");

        BadCredentialsException ex = assertThrows(BadCredentialsException.class, () ->
                authService.login(request, "127.0.0.1", "Safari"));

        assertEquals("Invalid email or password", ex.getMessage(), "Must not leak whether user or password was wrong");
        assertEquals(1, user.getFailedLoginAttempts());
        verify(userRepository).save(user);

        verify(auditService).recordEvent(
                eq(AuditEventType.LOGIN_FAILURE),
                eq("diana@themyscira.com"),
                eq("127.0.0.1"),
                eq("Safari"),
                eq("FAILURE"),
                contains("Invalid password attempt")
        );
    }

    @Test
    @DisplayName("Test 6b: Account lock triggered after 5 consecutive failed login attempts")
    void testAccountLockAfterMaxFailures() {
        String encodedPassword = passwordEncoder.encode("RealPassword123!");
        User user = new User("barry@starlabs.com", encodedPassword, "Barry", "Allen", "+15552223333");
        user.setId(UUID.randomUUID());
        user.setFailedLoginAttempts(4); // 4 prior failures; next one is 5th!

        when(userRepository.findByEmailIgnoreCase("barry@starlabs.com")).thenReturn(Optional.of(user));

        LoginRequest request = new LoginRequest("barry@starlabs.com", "WrongPassword!");

        assertThrows(LockedException.class, () -> authService.login(request, "127.0.0.1", "Chrome"));

        assertEquals(5, user.getFailedLoginAttempts());
        assertEquals(UserStatus.LOCKED, user.getStatus());
        assertNotNull(user.getLockedUntil());
        assertTrue(user.getLockedUntil().isAfter(Instant.now()));

        verify(auditService).recordEvent(
                eq(AuditEventType.ACCOUNT_LOCKED),
                eq("barry@starlabs.com"),
                eq("127.0.0.1"),
                eq("Chrome"),
                eq("FAILURE"),
                contains("Account locked")
        );
    }
}

