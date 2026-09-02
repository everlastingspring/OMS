package com.oms.audit.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.oms.audit.document.ApplicationLog;
import com.oms.audit.repository.ApplicationLogRepository;
import com.oms.common.event.EventType;
import com.oms.common.event.OrderEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("SqsOrderNotificationConsumer")
class SqsOrderNotificationConsumerTest {

    @Mock
    private ApplicationLogRepository applicationLogRepository;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @InjectMocks
    private SqsOrderNotificationConsumer consumer;

    private String validMessage() throws Exception {
        OrderEvent event = new OrderEvent();
        event.setEventId("evt-sqs-001");
        event.setEventType(EventType.ORDER_UPDATED);
        event.setOrderId(5L);
        event.setOrderNumber("ORD-005");
        event.setCurrentStatus("CONFIRMED");
        return objectMapper.writeValueAsString(event);
    }

    @Test
    @DisplayName("parses valid JSON and saves an application log with sqs source")
    void consume_validJson_savesApplicationLog() throws Exception {
        consumer.consume(validMessage());

        ArgumentCaptor<ApplicationLog> captor = ArgumentCaptor.forClass(ApplicationLog.class);
        verify(applicationLogRepository).save(captor.capture());
        ApplicationLog saved = captor.getValue();

        assertThat(saved.getSource()).isEqualTo("sqs");
        assertThat(saved.getStatus()).isEqualTo("SUCCESS");
        assertThat(saved.getService()).isEqualTo("audit-service");
        assertThat(saved.getEventType()).isEqualTo("ORDER_UPDATED");
        assertThat(saved.getOrderId()).isEqualTo("5");
        assertThat(saved.getMessage()).contains("ORD-005");
    }

    @Test
    @DisplayName("does not throw and does not save when the message JSON is malformed")
    void consume_invalidJson_swallowsErrorGracefully() {
        assertThatNoException().isThrownBy(() -> consumer.consume("{not-valid-json"));

        verify(applicationLogRepository, never()).save(any());
    }

    @Test
    @DisplayName("does not throw when eventType is missing and records UNKNOWN as the event type")
    void consume_missingEventType_recordsUnknown() throws Exception {
        String message = "{\"orderId\":3,\"orderNumber\":\"ORD-003\"}";

        assertThatNoException().isThrownBy(() -> consumer.consume(message));

        ArgumentCaptor<ApplicationLog> captor = ArgumentCaptor.forClass(ApplicationLog.class);
        verify(applicationLogRepository).save(captor.capture());
        assertThat(captor.getValue().getEventType()).isEqualTo("UNKNOWN");
    }
}
