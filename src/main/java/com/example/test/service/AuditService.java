package com.example.test.service;

import com.example.test.common.BusinessException;
import com.example.test.config.RequestLoggingFilter;
import com.example.test.dto.AuditEventResponse;
import com.example.test.dto.PageResponse;
import com.example.test.dto.search.AuditEventSearchCriteria;
import com.example.test.model.AuditAction;
import com.example.test.model.AuditEvent;
import com.example.test.model.AuditOutcome;
import com.example.test.model.Role;
import com.example.test.repo.AuditEventRepo;
import com.example.test.repo.spec.AuditEventSpecifications;
import com.example.test.security.CurrentUser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Writes and searches the audit trail.
 *
 * <p>Usage: {@code audit.event(TRANSFER_COMPLETED).actor(user).resource("TRANSACTION", ref).detail("amount", a)
 * .recordSuccess()}. Request id, client IP and user agent are captured automatically.
 *
 * <p>Transaction semantics matter for an audit trail:
 * <ul>
 *   <li>{@link Entry#recordSuccess()} and {@link Entry#recordFailure(String)} write in the caller's transaction,
 *       so the record exists only if the business change it describes was committed.</li>
 *   <li>{@link Entry#recordFailureDespiteRollback(BusinessException)} writes in its own transaction, so a failed
 *       attempt is still recorded although the caller's transaction is about to roll back.</li>
 * </ul>
 * Every event is also written to the {@code AUDIT} logger.
 */
@Service
public class AuditService {

    private static final Logger AUDIT_LOG = LoggerFactory.getLogger("AUDIT");
    private static final Logger log = LoggerFactory.getLogger(AuditService.class);
    private static final int MAX_DETAILS = 2000;
    private static final int MAX_USER_AGENT = 256;

    private final AuditEventRepo auditEventRepo;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate independentTransaction;

    public AuditService(AuditEventRepo auditEventRepo, ObjectMapper objectMapper,
                        PlatformTransactionManager transactionManager) {
        this.auditEventRepo = auditEventRepo;
        this.objectMapper = objectMapper;
        this.independentTransaction = new TransactionTemplate(transactionManager);
        this.independentTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public Entry event(AuditAction action) {
        return new Entry(action);
    }

    @Transactional(readOnly = true)
    public PageResponse<AuditEventResponse> search(AuditEventSearchCriteria criteria, int page, int size) {
        return PageResponse.from(
                auditEventRepo.findAll(AuditEventSpecifications.matching(criteria),
                        PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"))),
                AuditEventResponse::from);
    }

    /** Builder for one audit event. */
    public final class Entry {

        private final AuditAction action;
        private final Map<String, Object> details = new LinkedHashMap<>();
        private Long actorUserId;
        private Role actorRole;
        private String resourceType;
        private String resourceId;

        private Entry(AuditAction action) {
            this.action = action;
        }

        public Entry actor(CurrentUser user) {
            return user == null ? this : actor(user.id(), user.role());
        }

        public Entry actor(Long userId, Role role) {
            this.actorUserId = userId;
            this.actorRole = role;
            return this;
        }

        public Entry resource(String type, String id) {
            this.resourceType = type;
            this.resourceId = id;
            return this;
        }

        /** Adds a fact to the event. Null values are skipped. Never pass secrets (passwords, PINs, tokens). */
        public Entry detail(String key, Object value) {
            if (value != null) {
                details.put(key, value);
            }
            return this;
        }

        /** Saved in the caller's transaction: kept only if the business change commits. */
        public void recordSuccess() {
            save(build(AuditOutcome.SUCCESS, null));
        }

        /** A failure saved in the caller's transaction, for callers that commit despite the failure. */
        public void recordFailure(String errorCode) {
            save(build(AuditOutcome.FAILURE, errorCode));
        }

        /**
         * A failure saved in a separate transaction, so it survives the caller's rollback. Never throws: an audit
         * problem must not hide the original error from the client.
         */
        public void recordFailureDespiteRollback(BusinessException error) {
            detail("message", error.getMessage());
            AuditEvent event = build(AuditOutcome.FAILURE, error.getErrorCode().name());
            try {
                independentTransaction.executeWithoutResult(status -> save(event));
            } catch (RuntimeException ex) {
                log.error("Could not write audit event {} {}", event.getAction(), event.getOutcome(), ex);
            }
        }

        private AuditEvent build(AuditOutcome outcome, String errorCode) {
            HttpServletRequest request = currentRequest();
            return AuditEvent.builder()
                    .occurredAt(Instant.now())
                    .action(action)
                    .outcome(outcome)
                    .actorUserId(actorUserId)
                    .actorRole(actorRole)
                    .resourceType(resourceType)
                    .resourceId(resourceId)
                    .errorCode(errorCode)
                    .details(toJson(details))
                    .requestId(MDC.get(RequestLoggingFilter.MDC_REQUEST_ID))
                    .ipAddress(request != null ? request.getRemoteAddr() : null)
                    .userAgent(request != null ? truncate(request.getHeader("User-Agent"), MAX_USER_AGENT) : null)
                    .build();
        }
    }

    private void save(AuditEvent event) {
        auditEventRepo.save(event);
        AUDIT_LOG.info("action={} outcome={} actor={} role={} resource={}:{} errorCode={} details={}",
                event.getAction(), event.getOutcome(), event.getActorUserId(), event.getActorRole(),
                event.getResourceType(), event.getResourceId(), event.getErrorCode(), event.getDetails());
    }

    private String toJson(Map<String, Object> details) {
        if (details.isEmpty()) {
            return null;
        }
        try {
            return truncate(objectMapper.writeValueAsString(details), MAX_DETAILS);
        } catch (JsonProcessingException ex) {
            return truncate(details.toString(), MAX_DETAILS);
        }
    }

    private static HttpServletRequest currentRequest() {
        return RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes
                ? attributes.getRequest()
                : null;
    }

    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}
