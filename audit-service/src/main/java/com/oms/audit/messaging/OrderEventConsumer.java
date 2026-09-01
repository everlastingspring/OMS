package com.oms.audit.messaging;

import com.oms.audit.service.AuditService;
import com.oms.common.event.KafkaTopics;
import com.oms.common.event.OrderEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderEventConsumer {

    private final AuditService auditService;

    @KafkaListener(
            topics = KafkaTopics.ORDER_EVENTS,
            groupId = "audit-service",
            containerFactory = "kafkaListenerContainerFactory")
    public void consume(ConsumerRecord<String, OrderEvent> record, Acknowledgment ack) {
        OrderEvent event = record.value();
        log.debug("Received {} event for order {} (eventId={})",
                event.getEventType(), event.getOrderNumber(), event.getEventId());
        try {
            auditService.recordOrderEvent(event);
            ack.acknowledge();
        } catch (Exception ex) {
            log.error("Failed to process event {} for order {}: {}",
                    event.getEventId(), event.getOrderNumber(), ex.getMessage());
            // Re-throw so DefaultErrorHandler applies backoff + DLT recovery
            throw ex;
        }
    }
}
