package com.thinklab.infrastructure.adapter.out.persistence.entity;

import com.thinklab.domain.model.AuditEntry;
import com.thinklab.domain.model.ElementStatus;
import io.micronaut.core.annotation.Introspected;

import java.time.Instant;

/** Persistence form of {@link AuditEntry}, shared by the node and edge documents. */
@Introspected
public record AuditEntryDocument(Instant occurredAt, String action, String executor,
                                 String fromStatus, String toStatus, String detail) {

    public static AuditEntryDocument fromDomain(AuditEntry entry) {
        return new AuditEntryDocument(entry.occurredAt(), entry.action(), entry.executor(),
                entry.fromStatus() != null ? entry.fromStatus().name() : null, entry.toStatus().name(), entry.detail());
    }

    public AuditEntry toDomain() {
        return new AuditEntry(occurredAt, action, executor,
                fromStatus != null ? ElementStatus.valueOf(fromStatus) : null, ElementStatus.valueOf(toStatus), detail);
    }
}
