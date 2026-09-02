package com.oms.order.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("OrderStatus state machine")
class OrderStatusTest {

    // ---- valid transitions ----

    @Test
    @DisplayName("PENDING → CONFIRMED is the only forward step from a new order")
    void pending_canTransitionTo_confirmed() {
        assertThat(OrderStatus.PENDING.canTransitionTo(OrderStatus.CONFIRMED)).isTrue();
    }

    @Test
    @DisplayName("CONFIRMED → SHIPPED once warehouse dispatches")
    void confirmed_canTransitionTo_shipped() {
        assertThat(OrderStatus.CONFIRMED.canTransitionTo(OrderStatus.SHIPPED)).isTrue();
    }

    @Test
    @DisplayName("SHIPPED → DELIVERED on courier handoff")
    void shipped_canTransitionTo_delivered() {
        assertThat(OrderStatus.SHIPPED.canTransitionTo(OrderStatus.DELIVERED)).isTrue();
    }

    // ---- invalid forward skips ----

    @Test
    @DisplayName("PENDING → SHIPPED skips CONFIRMED and is rejected")
    void pending_cannotSkipTo_shipped() {
        assertThat(OrderStatus.PENDING.canTransitionTo(OrderStatus.SHIPPED)).isFalse();
    }

    @Test
    @DisplayName("PENDING → DELIVERED skips two steps and is rejected")
    void pending_cannotSkipTo_delivered() {
        assertThat(OrderStatus.PENDING.canTransitionTo(OrderStatus.DELIVERED)).isFalse();
    }

    @Test
    @DisplayName("CONFIRMED → DELIVERED skips SHIPPED and is rejected")
    void confirmed_cannotSkipTo_delivered() {
        assertThat(OrderStatus.CONFIRMED.canTransitionTo(OrderStatus.DELIVERED)).isFalse();
    }

    // ---- terminal states allow nothing ----

    @Test
    @DisplayName("DELIVERED is terminal — no further transitions allowed")
    void delivered_cannotTransitionToAnything() {
        for (OrderStatus next : OrderStatus.values()) {
            assertThat(OrderStatus.DELIVERED.canTransitionTo(next))
                    .as("DELIVERED -> " + next)
                    .isFalse();
        }
    }

    @Test
    @DisplayName("CANCELLED is terminal — no further transitions allowed")
    void cancelled_cannotTransitionToAnything() {
        for (OrderStatus next : OrderStatus.values()) {
            assertThat(OrderStatus.CANCELLED.canTransitionTo(next))
                    .as("CANCELLED -> " + next)
                    .isFalse();
        }
    }

    // ---- cancellability ----

    @Test
    @DisplayName("PENDING is cancellable — user can still abort before fulfilment")
    void pending_isCancellable() {
        assertThat(OrderStatus.PENDING.isCancellable()).isTrue();
    }

    @Test
    @DisplayName("CONFIRMED is cancellable — warehouse has not dispatched yet")
    void confirmed_isCancellable() {
        assertThat(OrderStatus.CONFIRMED.isCancellable()).isTrue();
    }

    @Test
    @DisplayName("SHIPPED is not cancellable — goods already in transit")
    void shipped_isNotCancellable() {
        assertThat(OrderStatus.SHIPPED.isCancellable()).isFalse();
    }

    @Test
    @DisplayName("DELIVERED is not cancellable — order is complete")
    void delivered_isNotCancellable() {
        assertThat(OrderStatus.DELIVERED.isCancellable()).isFalse();
    }

    @Test
    @DisplayName("CANCELLED is not cancellable — already cancelled")
    void cancelled_isNotCancellable() {
        assertThat(OrderStatus.CANCELLED.isCancellable()).isFalse();
    }
}
