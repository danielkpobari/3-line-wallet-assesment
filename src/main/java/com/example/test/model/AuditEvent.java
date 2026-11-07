package com.example.test.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

import java.time.Instant;

/** One immutable audit record. Created only through {@link com.example.test.service.AuditService}. */
@Entity
@Immutable
@Table(name = "audit_events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50, updatable = false)
    private AuditAction action;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10, updatable = false)
    private AuditOutcome outcome;

    /** Null for anonymous actions (e.g. a login attempt for an unknown email) and system actions. */
    @Column(name = "actor_user_id", updatable = false)
    private Long actorUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "actor_role", length = 20, updatable = false)
    private Role actorRole;

    @Column(name = "resource_type", length = 30, updatable = false)
    private String resourceType;

    @Column(name = "resource_id", length = 64, updatable = false)
    private String resourceId;

    @Column(name = "error_code", length = 50, updatable = false)
    private String errorCode;

    /** Small JSON object with event-specific facts. Never contains passwords, PINs or tokens. */
    @Column(length = 2000, updatable = false)
    private String details;

    /** Same value as the X-Request-Id response header and the requestId in log lines. */
    @Column(name = "request_id", length = 64, updatable = false)
    private String requestId;

    @Column(name = "ip_address", length = 45, updatable = false)
    private String ipAddress;

    @Column(name = "user_agent", length = 256, updatable = false)
    private String userAgent;

    @Builder
    private AuditEvent(Instant occurredAt, AuditAction action, AuditOutcome outcome, Long actorUserId,
                       Role actorRole, String resourceType, String resourceId, String errorCode, String details,
                       String requestId, String ipAddress, String userAgent) {
        this.occurredAt = occurredAt;
        this.action = action;
        this.outcome = outcome;
        this.actorUserId = actorUserId;
        this.actorRole = actorRole;
        this.resourceType = resourceType;
        this.resourceId = resourceId;
        this.errorCode = errorCode;
        this.details = details;
        this.requestId = requestId;
        this.ipAddress = ipAddress;
        this.userAgent = userAgent;
    }
}
