package com.oms.audit.document;

import com.oms.common.event.EventType;
import com.oms.common.event.OrderItemPayload;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@Document(collection = "order_audit_logs")
public class OrderAuditLog {

    @Id
    private String id;

    @Indexed(unique = true)
    private String eventId;

    private EventType eventType;
    private Long orderId;
    private String orderNumber;
    private Long userId;
    private BigDecimal totalAmount;
    private String previousStatus;
    private String currentStatus;
    private String reason;
    private List<OrderItemPayload> items;
    private Instant occurredAt;
    private Instant processedAt = Instant.now();
}
