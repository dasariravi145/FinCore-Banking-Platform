package com.fincore.platform.security;

import com.fincore.platform.user.entity.Role;
import com.fincore.platform.user.entity.RoleName;
import com.fincore.platform.user.entity.User;
import com.fincore.platform.user.entity.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Task 7: JWT Token Provider Security Tests")
class JwtTokenProviderTest {

    private static final String SECRET_KEY = "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970";
    private static final String ISSUER = "fincore-test-issuer";
    private static final String AUDIENCE = "fincore-test-clients";
    private static final long EXPIRATION_MS = 60000; // 1 minute

    private JwtTokenProvider tokenProvider;

    @BeforeEach
    void setUp() {
        tokenProvider = new JwtTokenProvider(SECRET_KEY, ISSUER, AUDIENCE, EXPIRATION_MS);
    }

    @Test
    @DisplayName("Test 10a: Generates valid signed token with accurate claims")
    void testGenerateAndValidateToken() {
        UUID userId = UUID.randomUUID();
        UserPrincipal principal = new UserPrincipal(
                userId,
                "test@fincore.local",
                "hashedPass",
                "Alex",
                "Smith",
                UserStatus.ACTIVE,
                true,
                List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER"), new SimpleGrantedAuthority("PERM_USER_READ"))
        );

        String token = tokenProvider.generateToken(principal);

        assertNotNull(token);
        assertTrue(tokenProvider.validateToken(token));
        assertEquals(userId, tokenProvider.getUserIdFromToken(token));
        assertEquals("test@fincore.local", tokenProvider.getEmailFromToken(token));

        List<String> roles = tokenProvider.getRolesFromToken(token);
        assertTrue(roles.contains("ROLE_CUSTOMER"));
    }

    @Test
    @DisplayName("Test 10b: Rejects tampered or malformed token")
    void testTamperedTokenRejection() {
        UUID userId = UUID.randomUUID();
        UserPrincipal principal = new UserPrincipal(
                userId,
                "alex@fincore.local",
                "hashedPass",
                "Alex",
                "Smith",
                UserStatus.ACTIVE,
                true,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_CUSTOMER"))
        );

        String token = tokenProvider.generateToken(principal);
        String tamperedToken = token + "corrupted";

        assertFalse(tokenProvider.validateToken(tamperedToken));
    }

    @Test
    @DisplayName("Test 10c: Rejects expired token")
    void testExpiredTokenRejection() {
        // Create token provider with 1 millisecond expiration
        JwtTokenProvider shortLivedProvider = new JwtTokenProvider(SECRET_KEY, ISSUER, AUDIENCE, 1);

        UUID userId = UUID.randomUUID();
        UserPrincipal principal = new UserPrincipal(
                userId,
                "expired@fincore.local",
                "hashedPass",
                "Alex",
                "Smith",
                UserStatus.ACTIVE,
                true,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_CUSTOMER"))
        );

        String token = shortLivedProvider.generateToken(principal);

        // Sleep briefly to ensure expiration
        try {
            Thread.sleep(50);
        } catch (InterruptedException ignored) {}

        assertFalse(shortLivedProvider.validateToken(token));
    }

    @Test
    @DisplayName("Test 10d: Rejects token with mismatched issuer or audience")
    void testMismatchedIssuerOrAudience() {
        JwtTokenProvider differentIssuerProvider = new JwtTokenProvider(SECRET_KEY, "unauthorized-issuer", AUDIENCE, EXPIRATION_MS);

        UUID userId = UUID.randomUUID();
        UserPrincipal principal = new UserPrincipal(
                userId,
                "issuer@fincore.local",
                "hashedPass",
                "Alex",
                "Smith",
                UserStatus.ACTIVE,
                true,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_CUSTOMER"))
        );

        String foreignToken = differentIssuerProvider.generateToken(principal);

        // Validating with standard provider should fail due to issuer mismatch
        assertFalse(tokenProvider.validateToken(foreignToken));
    }
}

