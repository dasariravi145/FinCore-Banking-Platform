package com.fincore.platform.ledger.repository;

import com.fincore.platform.ledger.entity.LedgerEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Repository
public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, UUID> {

    List<LedgerEntry> findByTransaction_Id(UUID transactionId);

    List<LedgerEntry> findByAccount_IdOrderByCreatedAtAsc(UUID accountId);

    Page<LedgerEntry> findByAccount_AccountNumberOrderByCreatedAtDesc(String accountNumber, Pageable pageable);

    @Query("SELECT COALESCE(SUM(CASE " +
           "  WHEN le.account.accountCategory = com.fincore.platform.account.entity.AccountCategory.ASSET THEN " +
           "       (CASE WHEN le.entryType = com.fincore.platform.ledger.entity.LedgerEntryType.DEBIT THEN le.amount ELSE -le.amount END) " +
           "  WHEN le.account.accountCategory = com.fincore.platform.account.entity.AccountCategory.EXPENSE THEN " +
           "       (CASE WHEN le.entryType = com.fincore.platform.ledger.entity.LedgerEntryType.DEBIT THEN le.amount ELSE -le.amount END) " +
           "  ELSE " +
           "       (CASE WHEN le.entryType = com.fincore.platform.ledger.entity.LedgerEntryType.CREDIT THEN le.amount ELSE -le.amount END) " +
           "END), 0.00) " +
           "FROM LedgerEntry le WHERE le.account.id = :accountId")
    BigDecimal calculateReconciledBalance(@Param("accountId") UUID accountId);

    @Query("SELECT COALESCE(SUM(le.amount), 0.00) FROM LedgerEntry le " +
           "WHERE le.transaction.id = :txId AND le.entryType = com.fincore.platform.ledger.entity.LedgerEntryType.DEBIT")
    BigDecimal sumDebitsByTransactionId(@Param("txId") UUID txId);

    @Query("SELECT COALESCE(SUM(le.amount), 0.00) FROM LedgerEntry le " +
           "WHERE le.transaction.id = :txId AND le.entryType = com.fincore.platform.ledger.entity.LedgerEntryType.CREDIT")
    BigDecimal sumCreditsByTransactionId(@Param("txId") UUID txId);
}
