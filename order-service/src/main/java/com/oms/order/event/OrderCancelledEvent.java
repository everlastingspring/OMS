package com.oms.order.event;

import com.oms.order.entity.Order;
import org.springframework.context.ApplicationEvent;

public class OrderCancelledEvent extends ApplicationEvent {

    private final Order order;

    public OrderCancelledEvent(Object source, Order order) {
        super(source);
        this.order = order;
    }

    public Order getOrder() {
        return order;
    }
}
