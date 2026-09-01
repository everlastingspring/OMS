package com.oms.audit.service.impl;

import com.oms.audit.document.ActivityHistory;
import com.oms.audit.document.ApplicationLog;
import com.oms.audit.document.OrderAuditLog;
import com.oms.audit.repository.ActivityHistoryRepository;
import com.oms.audit.repository.ApplicationLogRepository;
import com.oms.audit.repository.OrderAuditLogRepository;
import com.oms.audit.service.AuditService;
import com.oms.common.event.OrderEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditServiceImpl implements AuditService {

    private final OrderAuditLogRepository auditLogRepository;
    private final ActivityHistoryRepository activityHistoryRepository;
    private final ApplicationLogRepository applicationLogRepository;

    @Override
    public void recordOrderEvent(OrderEvent event) {
        // Idempotency: unique index on eventId — duplicate redelivery is a no-op
        try {
            OrderAuditLog auditLog = toAuditLog(event);
            auditLogRepository.save(auditLog);

            ActivityHistory activity = toActivityHistory(event);
            activityHistoryRepository.save(activity);

            applicationLogRepository.save(toAppLog(event, "SUCCESS", "kafka"));

            log.info("Recorded {} event for order {}", event.getEventType(), event.getOrderNumber());
        } catch (DuplicateKeyException ex) {
            log.info("Duplicate event {} for order {} — skipping (idempotent)",
                    event.getEventId(), event.getOrderNumber());
        }
    }

    @Override
    public List<OrderAuditLog> getAuditTrailForOrder(Long orderId) {
        return auditLogRepository.findByOrderIdOrderByOccurredAtDesc(orderId);
    }

    @Override
    public List<ActivityHistory> getActivityForUser(Long userId) {
        return activityHistoryRepository.findByUserIdOrderByTimestampDesc(userId);
    }

    private OrderAuditLog toAuditLog(OrderEvent event) {
        OrderAuditLog log = new OrderAuditLog();
        log.setEventId(event.getEventId());
        log.setEventType(event.getEventType());
        log.setOrderId(event.getOrderId());
        log.setOrderNumber(event.getOrderNumber());
        log.setUserId(event.getUserId());
        log.setTotalAmount(event.getTotalAmount());
        log.setPreviousStatus(event.getPreviousStatus());
        log.setCurrentStatus(event.getCurrentStatus());
        log.setReason(event.getReason());
        log.setItems(event.getItems());
        log.setOccurredAt(event.getOccurredAt());
        return log;
    }

    private ActivityHistory toActivityHistory(OrderEvent event) {
        ActivityHistory activity = new ActivityHistory();
        activity.setUserId(event.getUserId());
        activity.setAction(event.getEventType().name());
        activity.setEntityType("ORDER");
        activity.setEntityId(event.getOrderId());
        activity.setOrderNumber(event.getOrderNumber());
        activity.setDetail(String.format("Order %s status: %s", event.getOrderNumber(), event.getCurrentStatus()));
        return activity;
    }

    private ApplicationLog toAppLog(OrderEvent event, String status, String source) {
        ApplicationLog appLog = new ApplicationLog();
        appLog.setService("audit-service");
        appLog.setEventType(event.getEventType().name());
        appLog.setOrderId(String.valueOf(event.getOrderId()));
        appLog.setMessage(String.format("Processed %s for order %s", event.getEventType(), event.getOrderNumber()));
        appLog.setStatus(status);
        appLog.setSource(source);
        return appLog;
    }
}
