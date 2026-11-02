package com.fincore.platform.fraud.repository;

import com.fincore.platform.fraud.entity.FraudAssessment;
import com.fincore.platform.fraud.entity.FraudAssessmentStatus;
import com.fincore.platform.fraud.entity.FraudDecision;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.UUID;

@Repository
public interface FraudAssessmentRepository extends JpaRepository<FraudAssessment, UUID> {

    Page<FraudAssessment> findByStatusOrderByCreatedAtDesc(FraudAssessmentStatus status, Pageable pageable);

    Page<FraudAssessment> findByDecisionOrderByCreatedAtDesc(FraudDecision decision, Pageable pageable);

    @Query("SELECT COUNT(fa) FROM FraudAssessment fa WHERE fa.sourceAccount.id = :accountId AND fa.createdAt >= :since")
    long countRecentAssessmentsBySourceAccount(@Param("accountId") UUID accountId, @Param("since") Instant since);
}

