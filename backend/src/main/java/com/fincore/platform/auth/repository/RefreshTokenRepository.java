package com.fincore.platform.auth.repository;

import com.fincore.platform.auth.entity.RefreshToken;
import com.fincore.platform.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    List<RefreshToken> findAllByUser(User user);

    @Modifying
    @Query("UPDATE RefreshToken r SET r.revokedAt = :now, r.revokedReason = :reason WHERE r.user = :user AND r.revokedAt IS NULL")
    int revokeAllForUser(@Param("user") User user, @Param("reason") String reason, @Param("now") Instant now);
}
