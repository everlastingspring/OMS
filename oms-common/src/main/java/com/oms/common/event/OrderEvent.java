package com.oms.common.event;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Single event payload published to Kafka for every order state change.
 * One shape rather than three keeps the consumer and the audit documents simple.
 *
 * eventId is the idempotency key: the audit-service holds a unique index on it,
 * so a redelivered message is written once.
 */
@Getter
@Setter
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OrderEvent {

    private String eventId = UUID.randomUUID().toString();
    private EventType eventType;
    private Instant occurredAt = Instant.now();
    private String correlationId;

    private Long orderId;
    private String orderNumber;
    private Long userId;
    private BigDecimal totalAmount;

    private String previousStatus;
    private String currentStatus;
    private String reason;

    private List<OrderItemPayload> items;
}
