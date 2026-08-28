package com.oms.order.event;

import com.oms.order.entity.Order;
import com.oms.order.entity.OrderStatus;
import org.springframework.context.ApplicationEvent;

public class OrderUpdatedEvent extends ApplicationEvent {

    private final Order order;
    private final OrderStatus previousStatus;

    public OrderUpdatedEvent(Object source, Order order, OrderStatus previousStatus) {
        super(source);
        this.order = order;
        this.previousStatus = previousStatus;
    }

    public Order getOrder() { return order; }

    public OrderStatus getPreviousStatus() { return previousStatus; }
}
