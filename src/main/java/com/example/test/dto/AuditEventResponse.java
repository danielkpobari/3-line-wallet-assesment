package com.example.test.dto;

import com.example.test.model.AuditAction;
import com.example.test.model.AuditEvent;
import com.example.test.model.AuditOutcome;
import com.example.test.model.Role;

import java.time.Instant;

public record AuditEventResponse(
        Long id,
        Instant occurredAt,
        AuditAction action,
        AuditOutcome outcome,
        Long actorUserId,
        Role actorRole,
        String resourceType,
        String resourceId,
        String errorCode,
        String details,
        String requestId,
        String ipAddress,
        String userAgent) {

    public static AuditEventResponse from(AuditEvent event) {
        return new AuditEventResponse(
                event.getId(),
                event.getOccurredAt(),
                event.getAction(),
                event.getOutcome(),
                event.getActorUserId(),
                event.getActorRole(),
                event.getResourceType(),
                event.getResourceId(),
                event.getErrorCode(),
                event.getDetails(),
                event.getRequestId(),
                event.getIpAddress(),
                event.getUserAgent());
    }
}
