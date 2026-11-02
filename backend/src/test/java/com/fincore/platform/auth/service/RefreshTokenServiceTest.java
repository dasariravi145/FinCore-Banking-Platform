package com.fincore.platform.auth.service;

import com.fincore.platform.auth.entity.RefreshToken;
import com.fincore.platform.auth.repository.RefreshTokenRepository;
import com.fincore.platform.common.audit.AuditEventType;
import com.fincore.platform.common.audit.SecurityAuditService;
import com.fincore.platform.common.error.TokenRefreshException;
import com.fincore.platform.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Task 7: Refresh Token Rotation & Replay Attack Tests")
class RefreshTokenServiceTest {

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private SecurityAuditService auditService;

    private RefreshTokenService refreshTokenService;
    private User testUser;

    @BeforeEach
    void setUp() {
        refreshTokenService = new RefreshTokenService(refreshTokenRepository, auditService, 604800000L);

        testUser = new User("sarah@fincore.local", "hashedPassword", "Sarah", "Connor", "+14155551234");
        testUser.setId(UUID.randomUUID());
    }

    @Test
    @DisplayName("Test 11a: Generates opaque token and stores SHA-256 hash in database")
    void testCreateRefreshToken() {
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

        String rawToken = refreshTokenService.createRefreshToken(testUser, "127.0.0.1", "JUnit5");

        assertNotNull(rawToken);
        assertFalse(rawToken.isBlank());

        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(captor.capture());

        RefreshToken savedToken = captor.getValue();
        assertEquals(testUser, savedToken.getUser());
        assertEquals("127.0.0.1", savedToken.getIpAddress());
        assertNotEquals(rawToken, savedToken.getTokenHash(), "Raw token must NEVER be stored plaintext in database");
        assertEquals(refreshTokenService.hashToken(rawToken), savedToken.getTokenHash());
    }

    @Test
    @DisplayName("Test 11b: Rotates active refresh token and revokes predecessor")
    void testRotateRefreshTokenSuccess() {
        String rawToken = "sample-raw-active-token";
        String tokenHash = refreshTokenService.hashToken(rawToken);

        RefreshToken existingToken = new RefreshToken(testUser, tokenHash, Instant.now().plusSeconds(3600), "127.0.0.1", "Chrome");
        existingToken.setId(UUID.randomUUID());

        when(refreshTokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(existingToken));
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(inv -> {
            RefreshToken t = inv.getArgument(0);
            if (t.getId() == null) {
                t.setId(UUID.randomUUID());
            }
            return t;
        });

        RefreshTokenService.RotationResult result =
                refreshTokenService.rotateRefreshToken(rawToken, "127.0.0.1", "Chrome");

        assertNotNull(result);
        assertNotNull(result.rawRefreshToken());
        assertNotEquals(rawToken, result.rawRefreshToken());
        assertEquals(testUser, result.user());

        assertTrue(existingToken.isRevoked());
        assertEquals("ROTATED", existingToken.getRevokedReason());
        assertNotNull(existingToken.getReplacedByTokenId());

        verify(auditService).recordEvent(
                eq(AuditEventType.TOKEN_REFRESH),
                eq(testUser.getEmail()),
                eq("127.0.0.1"),
                eq("Chrome"),
                eq("SUCCESS"),
                anyString()
        );
    }

    @Test
    @DisplayName("Test 11c: Detects replay attack on already-revoked token, revokes all user sessions and alerts")
    void testReplayAttackDetection() {
        String replayedRawToken = "already-used-stolen-token";
        String tokenHash = refreshTokenService.hashToken(replayedRawToken);

        RefreshToken revokedToken = new RefreshToken(testUser, tokenHash, Instant.now().plusSeconds(3600), "127.0.0.1", "AttackerAgent");
        revokedToken.setId(UUID.randomUUID());
        revokedToken.revoke("ROTATED", UUID.randomUUID()); // Already revoked previously!

        when(refreshTokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(revokedToken));

        TokenRefreshException ex = assertThrows(TokenRefreshException.class, () ->
                refreshTokenService.rotateRefreshToken(replayedRawToken, "192.168.1.100", "AttackerAgent"));

        assertTrue(ex.getMessage().contains("compromise detected"));

        // Verify that ALL sessions for the victim user were revoked
        verify(refreshTokenRepository).revokeAllForUser(eq(testUser), eq("COMPROMISED_REPLAY_ATTACK"), any(Instant.class));

        // Verify high-severity security audit event was logged
        verify(auditService).recordEvent(
                eq(AuditEventType.REFRESH_TOKEN_REPLAY),
                eq(testUser.getEmail()),
                eq("192.168.1.100"),
                eq("AttackerAgent"),
                eq("FAILURE"),
                contains("Attempted reuse of revoked token")
        );
    }

    @Test
    @DisplayName("Test 11d: Rejects expired refresh token")
    void testExpiredRefreshToken() {
        String rawToken = "expired-token";
        String tokenHash = refreshTokenService.hashToken(rawToken);

        RefreshToken expiredToken = new RefreshToken(testUser, tokenHash, Instant.now().minusSeconds(3600), "127.0.0.1", "Chrome");
        expiredToken.setId(UUID.randomUUID());

        when(refreshTokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(expiredToken));

        TokenRefreshException ex = assertThrows(TokenRefreshException.class, () ->
                refreshTokenService.rotateRefreshToken(rawToken, "127.0.0.1", "Chrome"));

        assertTrue(ex.getMessage().contains("expired"));
    }

    @Test
    @DisplayName("Test 12: Logout revokes session refresh token")
    void testLogoutRevokesToken() {
        String rawToken = "logout-target-token";
        String tokenHash = refreshTokenService.hashToken(rawToken);

        RefreshToken activeToken = new RefreshToken(testUser, tokenHash, Instant.now().plusSeconds(3600), "127.0.0.1", "Chrome");
        when(refreshTokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(activeToken));

        refreshTokenService.revokeToken(rawToken, "127.0.0.1", "Chrome");

        assertTrue(activeToken.isRevoked());
        assertEquals("USER_LOGOUT", activeToken.getRevokedReason());
        verify(refreshTokenRepository).save(activeToken);
        verify(auditService).recordEvent(
                eq(AuditEventType.LOGOUT),
                eq(testUser.getEmail()),
                eq("127.0.0.1"),
                eq("Chrome"),
                eq("SUCCESS"),
                anyString()
        );
    }
}

