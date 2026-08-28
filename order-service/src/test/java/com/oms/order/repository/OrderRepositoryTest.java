package com.oms.order.repository;

import com.oms.order.config.JpaAuditingConfig;
import com.oms.order.entity.Order;
import com.oms.order.entity.OrderItem;
import com.oms.order.entity.OrderStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(JpaAuditingConfig.class)
class OrderRepositoryTest {

    @Autowired
    private OrderRepository orderRepository;

    @Test
    void saveAndFindByOrderNumber() {
        Order order = buildOrder("ORD-20260828-0001");
        orderRepository.save(order);

        Optional<Order> found = orderRepository.findByOrderNumber("ORD-20260828-0001");
        assertThat(found).isPresent();
        assertThat(found.get().getUserId()).isEqualTo(2L);
    }

    @Test
    void findByUserId_returnsPagedOrders() {
        orderRepository.save(buildOrder("ORD-20260828-0002"));
        orderRepository.save(buildOrder("ORD-20260828-0003"));

        Page<Order> page = orderRepository.findByUserId(2L, PageRequest.of(0, 10));
        assertThat(page.getTotalElements()).isGreaterThanOrEqualTo(2);
    }

    @Test
    void findByUserIdAndStatus_filtersCorrectly() {
        Order order = buildOrder("ORD-20260828-0004");
        order.setStatus(OrderStatus.CONFIRMED);
        orderRepository.save(order);

        Page<Order> page = orderRepository.findByUserIdAndStatus(2L, OrderStatus.CONFIRMED, PageRequest.of(0, 10));
        assertThat(page.getContent()).allMatch(o -> o.getStatus() == OrderStatus.CONFIRMED);
    }

    private Order buildOrder(String orderNumber) {
        Order order = new Order();
        order.setOrderNumber(orderNumber);
        order.setUserId(2L);
        order.setStatus(OrderStatus.PENDING);
        order.setTotalAmount(new BigDecimal("199.99"));
        order.setShippingAddress("123 Test St");
        order.setPlacedAt(LocalDateTime.now());

        OrderItem item = new OrderItem();
        item.setProductId(1L);
        item.setSku("TEST-001");
        item.setProductName("Test Product");
        item.setQuantity(1);
        item.setUnitPrice(new BigDecimal("199.99"));
        item.setLineTotal(new BigDecimal("199.99"));
        order.addItem(item);

        return order;
    }
}
