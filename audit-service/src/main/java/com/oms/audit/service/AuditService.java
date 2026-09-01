package com.oms.audit.service;

import com.oms.audit.document.ActivityHistory;
import com.oms.audit.document.OrderAuditLog;
import com.oms.common.event.OrderEvent;

import java.util.List;

public interface AuditService {

    void recordOrderEvent(OrderEvent event);

    List<OrderAuditLog> getAuditTrailForOrder(Long orderId);

    List<ActivityHistory> getActivityForUser(Long userId);
}
