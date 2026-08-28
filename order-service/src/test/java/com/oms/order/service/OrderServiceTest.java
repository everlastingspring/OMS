package com.oms.order.service;

import com.oms.common.exception.ForbiddenException;
import com.oms.common.exception.InvalidOperationException;
import com.oms.common.security.UserPrincipal;
import com.oms.order.client.ProductServiceClient;
import com.oms.order.client.UserServiceClient;
import com.oms.order.client.dto.InternalUserResponse;
import com.oms.order.client.dto.StockLineResponse;
import com.oms.order.client.dto.StockOperationResponse;
import com.oms.order.dto.CancelOrderRequest;
import com.oms.order.dto.CreateOrderRequest;
import com.oms.order.dto.OrderItemRequest;
import com.oms.order.dto.OrderResponse;
import com.oms.order.dto.UpdateOrderStatusRequest;
import com.oms.order.entity.Order;
import com.oms.order.entity.OrderItem;
import com.oms.order.entity.OrderStatus;
import com.oms.order.repository.OrderRepository;
import com.oms.order.service.impl.OrderServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private UserServiceClient userClient;

    @Mock
    private ProductServiceClient productClient;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private OrderServiceImpl orderService;

    private UserPrincipal userPrincipal;
    private UserPrincipal adminPrincipal;

    @BeforeEach
    void setUp() {
        userPrincipal = new UserPrincipal(2L, "priya@oms.com", "USER");
        adminPrincipal = new UserPrincipal(1L, "admin@oms.com", "ADMIN");
    }

    @Test
    void createOrder_happyPath_returnsPendingOrder() {
        InternalUserResponse user = new InternalUserResponse();
        user.setId(2L);
        when(userClient.resolveUser(2L)).thenReturn(user);

        StockLineResponse line = new StockLineResponse();
        line.setProductId(1L);
        line.setSku("ELEC-001");
        line.setProductName("Widget");
        line.setQuantity(2);
        line.setUnitPrice(new BigDecimal("100.00"));
        line.setRemainingStock(8);

        StockOperationResponse stockResponse = new StockOperationResponse();
        stockResponse.setTotalAmount(new BigDecimal("200.00"));
        stockResponse.setLines(Collections.singletonList(line));
        when(productClient.reserveStock(any())).thenReturn(stockResponse);

        Order savedOrder = new Order();
        savedOrder.setId(1L);
        savedOrder.setOrderNumber("ORD-20260828-1234");
        savedOrder.setUserId(2L);
        savedOrder.setStatus(OrderStatus.PENDING);
        savedOrder.setTotalAmount(new BigDecimal("200.00"));
        savedOrder.setPlacedAt(LocalDateTime.now());
        savedOrder.setItems(Collections.emptyList());
        when(orderRepository.save(any(Order.class))).thenReturn(savedOrder);

        OrderItemRequest itemRequest = new OrderItemRequest();
        itemRequest.setProductId(1L);
        itemRequest.setQuantity(2);

        CreateOrderRequest request = new CreateOrderRequest();
        request.setItems(Collections.singletonList(itemRequest));
        request.setShippingAddress("123 Main St");

        OrderResponse response = orderService.createOrder(request, userPrincipal);

        assertThat(response.getStatus()).isEqualTo(OrderStatus.PENDING);
        verify(orderRepository).save(any(Order.class));
        verify(eventPublisher).publishEvent(any());
    }

    @Test
    void getOrder_wrongUser_throwsForbidden() {
        Order order = new Order();
        order.setId(10L);
        order.setUserId(99L);
        order.setStatus(OrderStatus.PENDING);
        order.setItems(Collections.emptyList());
        when(orderRepository.findById(10L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.getOrder(10L, userPrincipal))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void cancelOrder_setsStatusToCancelled() {
        Order order = new Order();
        order.setId(5L);
        order.setUserId(2L);
        order.setOrderNumber("ORD-20260828-5678");
        order.setStatus(OrderStatus.PENDING);

        OrderItem item = new OrderItem();
        item.setProductId(1L);
        item.setQuantity(2);
        order.setItems(Collections.singletonList(item));

        when(orderRepository.findById(5L)).thenReturn(Optional.of(order));

        CancelOrderRequest cancelRequest = new CancelOrderRequest();
        cancelRequest.setReason("Changed my mind");

        OrderResponse response = orderService.cancelOrder(5L, cancelRequest, userPrincipal);

        assertThat(response.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void updateStatus_invalidTransition_throwsInvalidOperation() {
        Order order = new Order();
        order.setId(3L);
        order.setUserId(2L);
        order.setStatus(OrderStatus.PENDING);
        order.setItems(Collections.emptyList());
        when(orderRepository.findById(3L)).thenReturn(Optional.of(order));

        UpdateOrderStatusRequest request = new UpdateOrderStatusRequest();
        request.setStatus(OrderStatus.DELIVERED);

        assertThatThrownBy(() -> orderService.updateStatus(3L, request, adminPrincipal))
                .isInstanceOf(InvalidOperationException.class)
                .hasMessageContaining("PENDING");
    }
}
