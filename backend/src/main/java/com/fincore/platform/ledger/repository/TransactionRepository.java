package com.fincore.platform.ledger.repository;

import com.fincore.platform.ledger.entity.Transaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    Optional<Transaction> findByTransactionReference(String transactionReference);

    Optional<Transaction> findByIdempotencyKey(String idempotencyKey);

    boolean existsByReversalOfTransaction_Id(UUID originalTxId);

    @Query("SELECT COUNT(t) FROM Transaction t WHERE t.sourceAccount.id = :accountId AND t.createdAt >= :since")
    long countRecentBySourceAccount(@Param("accountId") UUID accountId, @Param("since") Instant since);

    @Query("SELECT t FROM Transaction t WHERE (t.sourceAccount.id = :accountId OR t.destinationAccount.id = :accountId) " +
           "AND t.createdAt BETWEEN :from AND :to ORDER BY t.createdAt DESC")
    Page<Transaction> findByAccountIdAndDateRange(
            @Param("accountId") UUID accountId,
            @Param("from") Instant from,
            @Param("to") Instant to,
            Pageable pageable);

    @Query("SELECT t FROM Transaction t WHERE (t.sourceAccount.accountNumber = :accountNumber OR t.destinationAccount.accountNumber = :accountNumber) " +
           "ORDER BY t.createdAt DESC")
    Page<Transaction> findByAccountNumber(@Param("accountNumber") String accountNumber, Pageable pageable);

    @Query("SELECT t FROM Transaction t WHERE (t.sourceAccount.accountNumber = :accountNumber OR t.destinationAccount.accountNumber = :accountNumber) " +
           "AND t.createdAt BETWEEN :from AND :to ORDER BY t.createdAt DESC")
    Page<Transaction> findByAccountNumberAndDateRange(
            @Param("accountNumber") String accountNumber,
            @Param("from") Instant from,
            @Param("to") Instant to,
            Pageable pageable);
}

