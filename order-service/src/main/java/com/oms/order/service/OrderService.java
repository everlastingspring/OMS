package com.oms.order.service;

import com.oms.common.dto.PageResponse;
import com.oms.common.security.UserPrincipal;
import com.oms.order.dto.CancelOrderRequest;
import com.oms.order.dto.CreateOrderRequest;
import com.oms.order.dto.OrderResponse;
import com.oms.order.dto.UpdateOrderStatusRequest;
import com.oms.order.entity.OrderStatus;
import org.springframework.data.domain.Pageable;

public interface OrderService {

    OrderResponse createOrder(CreateOrderRequest request, UserPrincipal principal);

    OrderResponse getOrder(Long id, UserPrincipal principal);

    PageResponse<OrderResponse> getMyOrders(Long userId, OrderStatus status, Pageable pageable);

    PageResponse<OrderResponse> getAllOrders(OrderStatus status, Pageable pageable);

    OrderResponse updateStatus(Long id, UpdateOrderStatusRequest request, UserPrincipal principal);

    OrderResponse cancelOrder(Long id, CancelOrderRequest request, UserPrincipal principal);
}
