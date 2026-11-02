package com.fincore.platform.transfer.repository;

import com.fincore.platform.transfer.entity.IdempotencyRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, UUID> {

    Optional<IdempotencyRecord> findByIdempotencyKey(String idempotencyKey);

    Optional<IdempotencyRecord> findByIdempotencyKeyAndUserId(String idempotencyKey, UUID userId);

    void deleteByExpiresAtBefore(Instant now);
}

