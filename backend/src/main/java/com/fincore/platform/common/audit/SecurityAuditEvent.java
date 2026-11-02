package com.fincore.platform.common.audit;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "security_audit_events")
public class SecurityAuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 50)
    private AuditEventType eventType;

    @Column(name = "principal_identifier", nullable = false, length = 255)
    private String principalIdentifier;

    @Column(name = "ip_address", length = 64)
    private String ipAddress;

    @Column(name = "user_agent", length = 512)
    private String userAgent;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "event_timestamp", nullable = false, updatable = false)
    private Instant eventTimestamp = Instant.now();

    @Column(columnDefinition = "TEXT")
    private String details;

    public SecurityAuditEvent() {}

    public SecurityAuditEvent(AuditEventType eventType, String principalIdentifier, String ipAddress,
                              String userAgent, String status, String details) {
        this.eventType = eventType;
        this.principalIdentifier = principalIdentifier;
        this.ipAddress = ipAddress;
        this.userAgent = userAgent;
        this.status = status;
        this.details = details;
        this.eventTimestamp = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public AuditEventType getEventType() {
        return eventType;
    }

    public void setEventType(AuditEventType eventType) {
        this.eventType = eventType;
    }

    public String getPrincipalIdentifier() {
        return principalIdentifier;
    }

    public void setPrincipalIdentifier(String principalIdentifier) {
        this.principalIdentifier = principalIdentifier;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getEventTimestamp() {
        return eventTimestamp;
    }

    public void setEventTimestamp(Instant eventTimestamp) {
        this.eventTimestamp = eventTimestamp;
    }

    public String getDetails() {
        return details;
    }

    public void setDetails(String details) {
        this.details = details;
    }
}

