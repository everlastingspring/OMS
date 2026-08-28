package com.oms.order.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oms.common.event.OrderEvent;
import io.awspring.cloud.messaging.core.QueueMessagingTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SqsNotificationPublisher {

    private final QueueMessagingTemplate queueMessagingTemplate;
    private final ObjectMapper objectMapper;

    @Value("${oms.sqs.order-notifications-queue}")
    private String queueName;

    public void publish(OrderEvent orderEvent) {
        try {
            String payload = objectMapper.writeValueAsString(orderEvent);
            queueMessagingTemplate.convertAndSend(queueName, payload);
            log.info("SQS notification sent for order {}", orderEvent.getOrderNumber());
        } catch (JsonProcessingException ex) {
            log.warn("Failed to serialize order event for SQS: {}", ex.getMessage());
        } catch (Exception ex) {
            log.warn("SQS publish failed for order {}: {}", orderEvent.getOrderNumber(), ex.getMessage());
        }
    }
}
