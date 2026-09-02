package com.oms.audit.messaging;

import com.oms.audit.service.AuditService;
import com.oms.common.event.EventType;
import com.oms.common.event.OrderEvent;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderEventConsumer")
class OrderEventConsumerTest {

    @Mock
    private AuditService auditService;

    @Mock
    private Acknowledgment acknowledgment;

    @InjectMocks
    private OrderEventConsumer consumer;

    private ConsumerRecord<String, OrderEvent> record(OrderEvent event) {
        return new ConsumerRecord<>("oms.order.events", 0, 0L, "key", event);
    }

    private OrderEvent sampleEvent() {
        OrderEvent event = new OrderEvent();
        event.setEventId("evt-001");
        event.setEventType(EventType.ORDER_CREATED);
        event.setOrderId(1L);
        event.setOrderNumber("ORD-001");
        return event;
    }

    @Test
    @DisplayName("calls auditService and acknowledges on successful processing")
    void consume_success_callsServiceAndAcknowledges() {
        OrderEvent event = sampleEvent();

        consumer.consume(record(event), acknowledgment);

        verify(auditService).recordOrderEvent(event);
        verify(acknowledgment).acknowledge();
    }

    @Test
    @DisplayName("rethrows exception without acknowledging so DefaultErrorHandler can apply backoff and DLT")
    void consume_serviceThrows_rethrowsWithoutAcknowledging() {
        OrderEvent event = sampleEvent();
        RuntimeException failure = new RuntimeException("MongoDB down");
        doThrow(failure).when(auditService).recordOrderEvent(any(OrderEvent.class));

        assertThatThrownBy(() -> consumer.consume(record(event), acknowledgment))
                .isSameAs(failure);

        verify(acknowledgment, never()).acknowledge();
    }
}
