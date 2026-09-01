package com.oms.audit.config;

import com.oms.audit.document.ActivityHistory;
import com.oms.audit.document.ApplicationLog;
import com.oms.audit.document.OrderAuditLog;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.IndexOperations;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class MongoConfig {

    private final MongoTemplate mongoTemplate;

    @EventListener(ApplicationReadyEvent.class)
    public void ensureIndexes() {
        // Unique index on eventId — the idempotency gate
        IndexOperations auditOps = mongoTemplate.indexOps(OrderAuditLog.class);
        auditOps.ensureIndex(new Index().on("eventId", Sort.Direction.ASC).unique().named("idx_eventId_unique"));
        auditOps.ensureIndex(new Index().on("orderId", Sort.Direction.ASC).named("idx_orderId"));
        auditOps.ensureIndex(new Index().on("occurredAt", Sort.Direction.DESC).named("idx_occurredAt"));

        IndexOperations activityOps = mongoTemplate.indexOps(ActivityHistory.class);
        activityOps.ensureIndex(new Index().on("userId", Sort.Direction.ASC).named("idx_userId"));
        activityOps.ensureIndex(new Index().on("entityId", Sort.Direction.ASC).named("idx_entityId"));
        activityOps.ensureIndex(new Index().on("timestamp", Sort.Direction.DESC).named("idx_timestamp"));

        IndexOperations appLogOps = mongoTemplate.indexOps(ApplicationLog.class);
        appLogOps.ensureIndex(new Index().on("orderId", Sort.Direction.ASC).named("idx_orderId"));
        appLogOps.ensureIndex(new Index().on("timestamp", Sort.Direction.DESC).named("idx_timestamp"));

        log.info("MongoDB indexes ensured for order_audit_logs, activity_history, application_logs");
    }
}
