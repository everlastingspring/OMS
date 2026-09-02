package com.oms.order.messaging;

import com.oms.common.event.EventType;
import com.oms.common.event.OrderEvent;
import com.oms.order.entity.Order;
import com.oms.order.entity.OrderItem;
import com.oms.order.entity.OrderStatus;
import com.oms.order.event.OrderCancelledEvent;
import com.oms.order.event.OrderPlacedEvent;
import com.oms.order.event.OrderUpdatedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.math.BigDecimal;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderEventPublisher")
class OrderEventPublisherTest {

    @Mock
    private KafkaTemplate<String, OrderEvent> kafkaTemplate;

    @Mock
    private SqsNotificationPublisher sqsPublisher;

    @InjectMocks
    private OrderEventPublisher publisher;

    private Order sampleOrder() {
        OrderItem item = new OrderItem();
        item.setProductId(1L);
        item.setSku("ELEC-001");
        item.setProductName("Widget");
        item.setQuantity(2);
        item.setUnitPrice(new BigDecimal("100.00"));

        Order order = new Order();
        order.setId(10L);
        order.setOrderNumber("ORD-20260901-0001");
        order.setUserId(2L);
        order.setStatus(OrderStatus.PENDING);
        order.setTotalAmount(new BigDecimal("200.00"));
        order.setItems(Collections.singletonList(item));
        return order;
    }

    @Test
    @DisplayName("ORDER_CREATED: publishes to oms.order.events keyed by order number")
    void onOrderPlaced_publishesOrderCreatedEvent() {
        Order order = sampleOrder();

        publisher.onOrderPlaced(new OrderPlacedEvent(this, order));

        ArgumentCaptor<OrderEvent> captor = ArgumentCaptor.forClass(OrderEvent.class);
        verify(kafkaTemplate).send(eq("oms.order.events"), eq("ORD-20260901-0001"), captor.capture());

        OrderEvent sent = captor.getValue();
        assertThat(sent.getEventType()).isEqualTo(EventType.ORDER_CREATED);
        assertThat(sent.getOrderId()).isEqualTo(10L);
        assertThat(sent.getOrderNumber()).isEqualTo("ORD-20260901-0001");
        assertThat(sent.getUserId()).isEqualTo(2L);
        assertThat(sent.getTotalAmount()).isEqualByComparingTo("200.00");
        assertThat(sent.getCurrentStatus()).isEqualTo("PENDING");
        assertThat(sent.getPreviousStatus()).isNull();
        assertThat(sent.getItems()).hasSize(1);
        assertThat(sent.getItems().get(0).getSku()).isEqualTo("ELEC-001");
    }

    @Test
    @DisplayName("ORDER_CREATED: also sends SQS notification for new order placement")
    void onOrderPlaced_alsoPublishesToSqs() {
        publisher.onOrderPlaced(new OrderPlacedEvent(this, sampleOrder()));

        verify(sqsPublisher).publish(any(OrderEvent.class));
    }

    @Test
    @DisplayName("ORDER_UPDATED: publishes previousStatus and currentStatus, no SQS")
    void onOrderUpdated_publishesStatusTransition() {
        Order order = sampleOrder();
        order.setStatus(OrderStatus.CONFIRMED);

        publisher.onOrderUpdated(new OrderUpdatedEvent(this, order, OrderStatus.PENDING));

        ArgumentCaptor<OrderEvent> captor = ArgumentCaptor.forClass(OrderEvent.class);
        verify(kafkaTemplate).send(eq("oms.order.events"), eq("ORD-20260901-0001"), captor.capture());

        OrderEvent sent = captor.getValue();
        assertThat(sent.getEventType()).isEqualTo(EventType.ORDER_UPDATED);
        assertThat(sent.getPreviousStatus()).isEqualTo("PENDING");
        assertThat(sent.getCurrentStatus()).isEqualTo("CONFIRMED");

        verify(sqsPublisher, never()).publish(any());
    }

    @Test
    @DisplayName("ORDER_CANCELLED: publishes cancellation reason, no SQS")
    void onOrderCancelled_publishesReasonAndStatus() {
        Order order = sampleOrder();
        order.setStatus(OrderStatus.CANCELLED);
        order.setCancellationReason("Changed my mind");

        publisher.onOrderCancelled(new OrderCancelledEvent(this, order));

        ArgumentCaptor<OrderEvent> captor = ArgumentCaptor.forClass(OrderEvent.class);
        verify(kafkaTemplate).send(eq("oms.order.events"), eq("ORD-20260901-0001"), captor.capture());

        OrderEvent sent = captor.getValue();
        assertThat(sent.getEventType()).isEqualTo(EventType.ORDER_CANCELLED);
        assertThat(sent.getCurrentStatus()).isEqualTo("CANCELLED");
        assertThat(sent.getReason()).isEqualTo("Changed my mind");

        verify(sqsPublisher, never()).publish(any());
    }

    @Test
    @DisplayName("Kafka failure: logs error and does not rethrow — order creation is not rolled back")
    void onOrderPlaced_kafkaFails_doesNotThrow() {
        doThrow(new RuntimeException("Broker unavailable"))
                .when(kafkaTemplate).send(anyString(), anyString(), any(OrderEvent.class));

        assertThatNoException()
                .isThrownBy(() -> publisher.onOrderPlaced(new OrderPlacedEvent(this, sampleOrder())));
    }

    @Test
    @DisplayName("items are mapped into the event payload with all fields")
    void onOrderPlaced_itemsAreMappedCorrectly() {
        publisher.onOrderPlaced(new OrderPlacedEvent(this, sampleOrder()));

        ArgumentCaptor<OrderEvent> captor = ArgumentCaptor.forClass(OrderEvent.class);
        verify(kafkaTemplate).send(anyString(), anyString(), captor.capture());

        assertThat(captor.getValue().getItems()).satisfies(items -> {
            assertThat(items).hasSize(1);
            assertThat(items.get(0).getProductId()).isEqualTo(1L);
            assertThat(items.get(0).getSku()).isEqualTo("ELEC-001");
            assertThat(items.get(0).getProductName()).isEqualTo("Widget");
            assertThat(items.get(0).getQuantity()).isEqualTo(2);
            assertThat(items.get(0).getUnitPrice()).isEqualByComparingTo("100.00");
        });
    }
}
