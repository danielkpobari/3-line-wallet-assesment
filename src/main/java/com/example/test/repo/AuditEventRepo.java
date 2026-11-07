package com.example.test.repo;

import com.example.test.model.AuditEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface AuditEventRepo extends JpaRepository<AuditEvent, Long>, JpaSpecificationExecutor<AuditEvent> {
}
