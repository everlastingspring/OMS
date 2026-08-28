package com.oms.order.messaging;

import com.oms.common.event.EventType;
import com.oms.common.event.KafkaTopics;
import com.oms.common.event.OrderEvent;
import com.oms.common.event.OrderItemPayload;
import com.oms.order.entity.Order;
import com.oms.order.event.OrderCancelledEvent;
import com.oms.order.event.OrderPlacedEvent;
import com.oms.order.event.OrderUpdatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderEventPublisher {

    private final KafkaTemplate<String, OrderEvent> kafkaTemplate;
    private final SqsNotificationPublisher sqsPublisher;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderPlaced(OrderPlacedEvent event) {
        OrderEvent orderEvent = buildEvent(event.getOrder(), EventType.ORDER_CREATED);
        orderEvent.setCurrentStatus(event.getOrder().getStatus().name());
        publish(orderEvent);
        sqsPublisher.publish(orderEvent);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderUpdated(OrderUpdatedEvent event) {
        OrderEvent orderEvent = buildEvent(event.getOrder(), EventType.ORDER_UPDATED);
        orderEvent.setPreviousStatus(event.getPreviousStatus().name());
        orderEvent.setCurrentStatus(event.getOrder().getStatus().name());
        publish(orderEvent);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderCancelled(OrderCancelledEvent event) {
        OrderEvent orderEvent = buildEvent(event.getOrder(), EventType.ORDER_CANCELLED);
        orderEvent.setCurrentStatus(event.getOrder().getStatus().name());
        orderEvent.setReason(event.getOrder().getCancellationReason());
        publish(orderEvent);
    }

    private void publish(OrderEvent orderEvent) {
        try {
            kafkaTemplate.send(KafkaTopics.ORDER_EVENTS, orderEvent.getOrderNumber(), orderEvent);
            log.info("Published {} event for order {}", orderEvent.getEventType(), orderEvent.getOrderNumber());
        } catch (Exception ex) {
            log.error("Failed to publish {} event for order {}: {}",
                    orderEvent.getEventType(), orderEvent.getOrderNumber(), ex.getMessage());
        }
    }

    private OrderEvent buildEvent(Order order, EventType eventType) {
        OrderEvent event = new OrderEvent();
        event.setEventType(eventType);
        event.setOrderId(order.getId());
        event.setOrderNumber(order.getOrderNumber());
        event.setUserId(order.getUserId());
        event.setTotalAmount(order.getTotalAmount());

        List<OrderItemPayload> items = order.getItems().stream()
                .map(i -> new OrderItemPayload(i.getProductId(), i.getSku(), i.getProductName(),
                        i.getQuantity(), i.getUnitPrice()))
                .collect(Collectors.toList());
        event.setItems(items);
        return event;
    }
}
