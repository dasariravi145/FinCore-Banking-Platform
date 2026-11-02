package com.fincore.platform.auth.service;

import com.fincore.platform.auth.entity.RefreshToken;
import com.fincore.platform.auth.repository.RefreshTokenRepository;
import com.fincore.platform.common.audit.AuditEventType;
import com.fincore.platform.common.audit.SecurityAuditService;
import com.fincore.platform.common.error.TokenRefreshException;
import com.fincore.platform.user.entity.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Service managing cryptographically secure refresh token rotation and replay detection.
 * Raw tokens are never stored in the database; only SHA-256 hashes are persisted.
 */
@Service
public class RefreshTokenService {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);

    private final RefreshTokenRepository refreshTokenRepository;
    private final SecurityAuditService auditService;
    private final long refreshTokenExpirationMs;
    private final SecureRandom secureRandom = new SecureRandom();

    public RefreshTokenService(
            RefreshTokenRepository refreshTokenRepository,
            SecurityAuditService auditService,
            @Value("${fincore.security.jwt.refresh-token-expiration-ms:604800000}") long refreshTokenExpirationMs) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.auditService = auditService;
        this.refreshTokenExpirationMs = refreshTokenExpirationMs;
    }

    @Transactional
    public String createRefreshToken(User user, String ipAddress, String deviceInfo) {
        String rawToken = generateSecureTokenString();
        String tokenHash = hashToken(rawToken);

        Instant expiresAt = Instant.now().plusMillis(refreshTokenExpirationMs);
        RefreshToken refreshToken = new RefreshToken(user, tokenHash, expiresAt, ipAddress, deviceInfo);

        refreshTokenRepository.save(refreshToken);
        return rawToken;
    }

    @Transactional(noRollbackFor = TokenRefreshException.class)
    public RotationResult rotateRefreshToken(String rawRefreshToken, String ipAddress, String deviceInfo) {
        String tokenHash = hashToken(rawRefreshToken);

        RefreshToken token = refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new TokenRefreshException("Invalid refresh token"));

        User user = token.getUser();

        // Replay detection: If an already-revoked token is used, trigger compromise protocol
        if (token.isRevoked()) {
            log.error("SECURITY ALERT: Refresh token replay attack detected for user: {}", user.getEmail());

            // Revoke all remaining active tokens for this user immediately
            refreshTokenRepository.revokeAllForUser(user, "COMPROMISED_REPLAY_ATTACK", Instant.now());

            auditService.recordEvent(
                    AuditEventType.REFRESH_TOKEN_REPLAY,
                    user.getEmail(),
                    ipAddress,
                    deviceInfo,
                    "FAILURE",
                    "Attempted reuse of revoked token ID: " + token.getId() + ". All user sessions terminated."
            );

            throw new TokenRefreshException("Refresh token compromise detected. All active sessions have been terminated.");
        }

        if (token.isExpired()) {
            auditService.recordEvent(
                    AuditEventType.TOKEN_REFRESH,
                    user.getEmail(),
                    ipAddress,
                    deviceInfo,
                    "FAILURE",
                    "Refresh token expired"
            );
            throw new TokenRefreshException("Refresh token expired. Please authenticate again.");
        }

        // Issue new rotated token
        String newRawToken = generateSecureTokenString();
        String newTokenHash = hashToken(newRawToken);
        Instant newExpiresAt = Instant.now().plusMillis(refreshTokenExpirationMs);

        RefreshToken newToken = new RefreshToken(user, newTokenHash, newExpiresAt, ipAddress, deviceInfo);
        RefreshToken savedNewToken = refreshTokenRepository.save(newToken);

        // Mark old token as revoked and linked to the replacement
        token.revoke("ROTATED", savedNewToken.getId());
        refreshTokenRepository.save(token);

        auditService.recordEvent(
                AuditEventType.TOKEN_REFRESH,
                user.getEmail(),
                ipAddress,
                deviceInfo,
                "SUCCESS",
                "Rotated token ID: " + token.getId() + " to new token ID: " + savedNewToken.getId()
        );

        return new RotationResult(newRawToken, user);
    }

    @Transactional
    public void revokeToken(String rawRefreshToken, String ipAddress, String userAgent) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return;
        }

        String tokenHash = hashToken(rawRefreshToken);
        refreshTokenRepository.findByTokenHash(tokenHash).ifPresent(token -> {
            token.revoke("USER_LOGOUT", null);
            refreshTokenRepository.save(token);

            auditService.recordEvent(
                    AuditEventType.LOGOUT,
                    token.getUser().getEmail(),
                    ipAddress,
                    userAgent,
                    "SUCCESS",
                    "User logged out, refresh token revoked"
            );
        });
    }

    @Transactional
    public void revokeAllForUser(User user, String reason) {
        refreshTokenRepository.revokeAllForUser(user, reason, Instant.now());
    }

    private String generateSecureTokenString() {
        byte[] randomBytes = new byte[32];
        secureRandom.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    public String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }

    public record RotationResult(String rawRefreshToken, User user) {}
}

