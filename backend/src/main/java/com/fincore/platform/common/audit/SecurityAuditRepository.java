package com.fincore.platform.common.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface SecurityAuditRepository extends JpaRepository<SecurityAuditEvent, UUID> {
    Page<SecurityAuditEvent> findByPrincipalIdentifierOrderByEventTimestampDesc(String principalIdentifier, Pageable pageable);
    Page<SecurityAuditEvent> findAllByOrderByEventTimestampDesc(Pageable pageable);
}

