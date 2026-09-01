package com.oms.audit.repository;

import com.oms.audit.document.OrderAuditLog;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface OrderAuditLogRepository extends MongoRepository<OrderAuditLog, String> {

    Optional<OrderAuditLog> findByEventId(String eventId);

    List<OrderAuditLog> findByOrderIdOrderByOccurredAtDesc(Long orderId);

    List<OrderAuditLog> findByOrderNumberOrderByOccurredAtDesc(String orderNumber);
}
