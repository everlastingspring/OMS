package com.oms.audit.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oms.audit.document.ApplicationLog;
import com.oms.audit.repository.ApplicationLogRepository;
import com.oms.common.event.OrderEvent;
import io.awspring.cloud.messaging.listener.annotation.SqsListener;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SqsOrderNotificationConsumer {

    private final ApplicationLogRepository applicationLogRepository;
    private final ObjectMapper objectMapper;

    @SqsListener("${oms.sqs.order-notifications-queue:oms-order-notifications}")
    public void consume(String message) {
        try {
            OrderEvent event = objectMapper.readValue(message, OrderEvent.class);
            log.info("SQS notification received for order {} ({})", event.getOrderNumber(), event.getEventType());

            ApplicationLog appLog = new ApplicationLog();
            appLog.setService("audit-service");
            appLog.setEventType(event.getEventType() != null ? event.getEventType().name() : "UNKNOWN");
            appLog.setOrderId(String.valueOf(event.getOrderId()));
            appLog.setMessage("SQS notification processed for order " + event.getOrderNumber());
            appLog.setStatus("SUCCESS");
            appLog.setSource("sqs");
            applicationLogRepository.save(appLog);
        } catch (Exception ex) {
            log.warn("Failed to process SQS notification: {}", ex.getMessage());
        }
    }
}
