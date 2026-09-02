package com.oms.order.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.oms.common.event.EventType;
import com.oms.common.event.OrderEvent;
import io.awspring.cloud.messaging.core.QueueMessagingTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("SqsNotificationPublisher")
class SqsNotificationPublisherTest {

    @Mock
    private QueueMessagingTemplate queueMessagingTemplate;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @InjectMocks
    private SqsNotificationPublisher publisher;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(publisher, "queueName", "oms-order-notifications");
    }

    private OrderEvent sampleEvent() {
        OrderEvent event = new OrderEvent();
        event.setEventId("evt-001");
        event.setEventType(EventType.ORDER_CREATED);
        event.setOrderId(1L);
        event.setOrderNumber("ORD-001");
        event.setCurrentStatus("PENDING");
        return event;
    }

    @Test
    @DisplayName("serializes event to JSON and sends to the configured queue name")
    void publish_validEvent_sendsToQueue() {
        publisher.publish(sampleEvent());

        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(queueMessagingTemplate).convertAndSend(eq("oms-order-notifications"), payloadCaptor.capture());

        String payload = payloadCaptor.getValue();
        assertThat(payload).contains("ORDER_CREATED");
        assertThat(payload).contains("ORD-001");
        assertThat(payload).contains("evt-001");
    }

    @Test
    @DisplayName("does not throw when SQS is unavailable — fire-and-forget, order must not roll back")
    void publish_sqsUnavailable_swallowsException() {
        doThrow(new RuntimeException("LocalStack not running"))
                .when(queueMessagingTemplate).convertAndSend(anyString(), any(Object.class));

        assertThatNoException().isThrownBy(() -> publisher.publish(sampleEvent()));
    }

    @Test
    @DisplayName("does not send when the event cannot be serialized")
    void publish_serializationFails_doesNotCallSqs() throws Exception {
        // Corrupt the mapper so writeValueAsString throws
        doThrow(new com.fasterxml.jackson.core.JsonProcessingException("boom") {})
                .when(objectMapper).writeValueAsString(any());

        assertThatNoException().isThrownBy(() -> publisher.publish(sampleEvent()));
        verify(queueMessagingTemplate, never()).convertAndSend(anyString(), any(Object.class));
    }
}
