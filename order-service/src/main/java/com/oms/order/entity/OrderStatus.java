package com.oms.order.entity;

public enum OrderStatus {

    PENDING {
        @Override public boolean canTransitionTo(OrderStatus next) { return next == CONFIRMED; }
        @Override public boolean isCancellable() { return true; }
    },
    CONFIRMED {
        @Override public boolean canTransitionTo(OrderStatus next) { return next == SHIPPED; }
        @Override public boolean isCancellable() { return true; }
    },
    SHIPPED {
        @Override public boolean canTransitionTo(OrderStatus next) { return next == DELIVERED; }
        @Override public boolean isCancellable() { return false; }
    },
    DELIVERED {
        @Override public boolean canTransitionTo(OrderStatus next) { return false; }
        @Override public boolean isCancellable() { return false; }
    },
    CANCELLED {
        @Override public boolean canTransitionTo(OrderStatus next) { return false; }
        @Override public boolean isCancellable() { return false; }
    };

    public abstract boolean canTransitionTo(OrderStatus next);

    public abstract boolean isCancellable();
}
