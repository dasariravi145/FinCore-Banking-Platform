package com.fincore.platform.common.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Audit recording service with separate transaction boundary (REQUIRES_NEW).
 * Ensures security events (failed logins, replay attacks, revocations) are logged
 * even if the calling business transaction rolls back.
 */
@Service
public class SecurityAuditService {

    private static final Logger log = LoggerFactory.getLogger(SecurityAuditService.class);

    private final SecurityAuditRepository auditRepository;

    public SecurityAuditService(SecurityAuditRepository auditRepository) {
        this.auditRepository = auditRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordEvent(AuditEventType eventType,
                            String principalIdentifier,
                            String ipAddress,
                            String userAgent,
                            String status,
                            String details) {
        try {
            SecurityAuditEvent event = new SecurityAuditEvent(
                    eventType,
                    principalIdentifier != null ? principalIdentifier : "ANONYMOUS",
                    ipAddress,
                    userAgent != null && userAgent.length() > 512 ? userAgent.substring(0, 512) : userAgent,
                    status,
                    details
            );
            auditRepository.save(event);
            log.info("SECURITY_AUDIT: event={} principal={} status={} ip={}",
                    eventType, principalIdentifier, status, ipAddress);
        } catch (Exception ex) {
            log.error("Failed to write security audit event: {}", ex.getMessage(), ex);
        }
    }

    @Transactional(readOnly = true)
    public Page<SecurityAuditEvent> getRecentEvents(Pageable pageable) {
        return auditRepository.findAllByOrderByEventTimestampDesc(pageable);
    }
}

