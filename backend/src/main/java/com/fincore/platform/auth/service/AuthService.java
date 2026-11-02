package com.fincore.platform.auth.service;

import com.fincore.platform.auth.dto.*;
import com.fincore.platform.common.audit.AuditEventType;
import com.fincore.platform.common.audit.SecurityAuditService;
import com.fincore.platform.common.error.ResourceNotFoundException;
import com.fincore.platform.common.error.UserAlreadyExistsException;
import com.fincore.platform.security.JwtTokenProvider;
import com.fincore.platform.security.UserPrincipal;
import com.fincore.platform.user.entity.Role;
import com.fincore.platform.user.entity.RoleName;
import com.fincore.platform.user.entity.User;
import com.fincore.platform.user.repository.RoleRepository;
import com.fincore.platform.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.UUID;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final long LOCK_TIME_DURATION_MINUTES = 15;

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;
    private final RefreshTokenService refreshTokenService;
    private final SecurityAuditService auditService;

    public AuthService(UserRepository userRepository,
                       RoleRepository roleRepository,
                       PasswordEncoder passwordEncoder,
                       JwtTokenProvider tokenProvider,
                       RefreshTokenService refreshTokenService,
                       SecurityAuditService auditService) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenProvider = tokenProvider;
        this.refreshTokenService = refreshTokenService;
        this.auditService = auditService;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request, String ipAddress, String userAgent) {
        String normalizedEmail = request.getEmail().trim().toLowerCase();

        if (userRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            auditService.recordEvent(
                    AuditEventType.REGISTRATION_FAILURE,
                    normalizedEmail,
                    ipAddress,
                    userAgent,
                    "FAILURE",
                    "Email already registered: " + normalizedEmail
            );
            throw new UserAlreadyExistsException("User with email " + normalizedEmail + " already exists");
        }

        // Security rule: Public registration ONLY creates CUSTOMER role. Admin promotion requires internal approval.
        Role customerRole = roleRepository.findByName(RoleName.ROLE_CUSTOMER)
                .orElseThrow(() -> new IllegalStateException("ROLE_CUSTOMER is not configured in database"));

        User user = new User(
                normalizedEmail,
                passwordEncoder.encode(request.getPassword()),
                request.getFirstName().trim(),
                request.getLastName().trim(),
                request.getPhoneNumber() != null ? request.getPhoneNumber().trim() : null
        );
        user.setRoles(Collections.singleton(customerRole));

        User savedUser = userRepository.save(user);

        auditService.recordEvent(
                AuditEventType.REGISTRATION_SUCCESS,
                savedUser.getEmail(),
                ipAddress,
                userAgent,
                "SUCCESS",
                "New customer registered with ID: " + savedUser.getId()
        );

        UserPrincipal principal = UserPrincipal.create(savedUser);
        String accessToken = tokenProvider.generateToken(principal);
        String refreshToken = refreshTokenService.createRefreshToken(savedUser, ipAddress, userAgent);

        return new AuthResponse(
                accessToken,
                refreshToken,
                tokenProvider.getAccessTokenExpirationMs() / 1000L,
                UserProfileResponse.fromUser(savedUser)
        );
    }

    @Transactional
    public AuthResponse login(LoginRequest request, String ipAddress, String userAgent) {
        String normalizedEmail = request.getEmail().trim().toLowerCase();

        User user = userRepository.findByEmailIgnoreCase(normalizedEmail)
                .orElse(null);

        if (user == null) {
            auditService.recordEvent(
                    AuditEventType.LOGIN_FAILURE,
                    normalizedEmail,
                    ipAddress,
                    userAgent,
                    "FAILURE",
                    "User account does not exist"
            );
            // Non-enumerating error message
            throw new BadCredentialsException("Invalid email or password");
        }

        if (!user.isAccountNonLocked()) {
            auditService.recordEvent(
                    AuditEventType.LOGIN_FAILURE,
                    normalizedEmail,
                    ipAddress,
                    userAgent,
                    "FAILURE",
                    "Login attempt on locked account"
            );
            throw new LockedException("Account is temporarily locked. Try again later.");
        }

        if (!user.isAccountActive()) {
            auditService.recordEvent(
                    AuditEventType.LOGIN_FAILURE,
                    normalizedEmail,
                    ipAddress,
                    userAgent,
                    "FAILURE",
                    "Login attempt on inactive/suspended account"
            );
            throw new DisabledException("Account is inactive or suspended.");
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            user.incrementFailedAttempts();

            if (user.getFailedLoginAttempts() >= MAX_FAILED_ATTEMPTS) {
                user.lockForDuration(LOCK_TIME_DURATION_MINUTES);
                userRepository.save(user);

                auditService.recordEvent(
                        AuditEventType.ACCOUNT_LOCKED,
                        normalizedEmail,
                        ipAddress,
                        userAgent,
                        "FAILURE",
                        "Account locked for " + LOCK_TIME_DURATION_MINUTES + " minutes after " + MAX_FAILED_ATTEMPTS + " failed attempts"
                );
                throw new LockedException("Account has been locked due to multiple failed login attempts.");
            }

            userRepository.save(user);

            auditService.recordEvent(
                    AuditEventType.LOGIN_FAILURE,
                    normalizedEmail,
                    ipAddress,
                    userAgent,
                    "FAILURE",
                    "Invalid password attempt count: " + user.getFailedLoginAttempts()
            );

            throw new BadCredentialsException("Invalid email or password");
        }

        // Successful authentication
        user.resetFailedAttempts();
        userRepository.save(user);

        auditService.recordEvent(
                AuditEventType.LOGIN_SUCCESS,
                normalizedEmail,
                ipAddress,
                userAgent,
                "SUCCESS",
                "User successfully authenticated"
        );

        UserPrincipal principal = UserPrincipal.create(user);
        String accessToken = tokenProvider.generateToken(principal);
        String refreshToken = refreshTokenService.createRefreshToken(user, ipAddress, userAgent);

        return new AuthResponse(
                accessToken,
                refreshToken,
                tokenProvider.getAccessTokenExpirationMs() / 1000L,
                UserProfileResponse.fromUser(user)
        );
    }

    @Transactional(noRollbackFor = com.fincore.platform.common.error.TokenRefreshException.class)
    public TokenRefreshResponse refreshToken(RefreshTokenRequest request, String ipAddress, String userAgent) {
        RefreshTokenService.RotationResult result =
                refreshTokenService.rotateRefreshToken(request.getRefreshToken(), ipAddress, userAgent);

        UserPrincipal principal = UserPrincipal.create(result.user());
        String newAccessToken = tokenProvider.generateToken(principal);

        return new TokenRefreshResponse(
                newAccessToken,
                result.rawRefreshToken(),
                tokenProvider.getAccessTokenExpirationMs() / 1000L
        );
    }

    @Transactional
    public void logout(LogoutRequest request, String ipAddress, String userAgent) {
        if (request != null && request.getRefreshToken() != null) {
            refreshTokenService.revokeToken(request.getRefreshToken(), ipAddress, userAgent);
        }
    }

    @Transactional(readOnly = true)
    public UserProfileResponse getCurrentUserProfile(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + userId));

        return UserProfileResponse.fromUser(user);
    }
}

