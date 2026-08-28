package com.oms.order.service.impl;

import com.oms.common.dto.PageResponse;
import com.oms.common.exception.ForbiddenException;
import com.oms.common.exception.InvalidOperationException;
import com.oms.common.exception.ResourceNotFoundException;
import com.oms.common.security.UserPrincipal;
import com.oms.order.client.ProductServiceClient;
import com.oms.order.client.UserServiceClient;
import com.oms.order.client.dto.StockItemRequest;
import com.oms.order.client.dto.StockLineResponse;
import com.oms.order.client.dto.StockOperationRequest;
import com.oms.order.client.dto.StockOperationResponse;
import com.oms.order.dto.CancelOrderRequest;
import com.oms.order.dto.CreateOrderRequest;
import com.oms.order.dto.OrderItemRequest;
import com.oms.order.dto.OrderResponse;
import com.oms.order.dto.UpdateOrderStatusRequest;
import com.oms.order.entity.Order;
import com.oms.order.entity.OrderItem;
import com.oms.order.entity.OrderStatus;
import com.oms.order.event.OrderCancelledEvent;
import com.oms.order.event.OrderPlacedEvent;
import com.oms.order.event.OrderUpdatedEvent;
import com.oms.order.mapper.OrderMapper;
import com.oms.order.repository.OrderRepository;
import com.oms.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final UserServiceClient userClient;
    private final ProductServiceClient productClient;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional
    public OrderResponse createOrder(CreateOrderRequest request, UserPrincipal principal) {
        userClient.resolveUser(principal.getId());

        String orderNumber = generateOrderNumber();

        List<StockItemRequest> stockItems = request.getItems().stream()
                .map(i -> new StockItemRequest(i.getProductId(), i.getQuantity()))
                .collect(Collectors.toList());

        StockOperationResponse stockResponse = productClient.reserveStock(
                new StockOperationRequest(orderNumber, stockItems));

        Order order = new Order();
        order.setOrderNumber(orderNumber);
        order.setUserId(principal.getId());
        order.setStatus(OrderStatus.PENDING);
        order.setTotalAmount(stockResponse.getTotalAmount());
        order.setShippingAddress(request.getShippingAddress());
        order.setNotes(request.getNotes());
        order.setPlacedAt(LocalDateTime.now());

        for (StockLineResponse line : stockResponse.getLines()) {
            OrderItem item = new OrderItem();
            item.setProductId(line.getProductId());
            item.setSku(line.getSku());
            item.setProductName(line.getProductName());
            item.setQuantity(line.getQuantity());
            item.setUnitPrice(line.getUnitPrice());
            item.setLineTotal(line.getUnitPrice().multiply(BigDecimal.valueOf(line.getQuantity())));
            order.addItem(item);
        }

        Order saved = orderRepository.save(order);
        log.info("Order created id={} number={} user={} total={}",
                saved.getId(), saved.getOrderNumber(), saved.getUserId(), saved.getTotalAmount());

        eventPublisher.publishEvent(new OrderPlacedEvent(this, saved));
        return OrderMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public OrderResponse getOrder(Long id, UserPrincipal principal) {
        Order order = requireOrder(id);
        boolean isOwner = order.getUserId().equals(principal.getId());
        boolean isAdmin = principal.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        if (!isOwner && !isAdmin) {
            throw new ForbiddenException("You do not have access to this order");
        }
        return OrderMapper.toResponse(order);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<OrderResponse> getMyOrders(Long userId, OrderStatus status, Pageable pageable) {
        Page<Order> page = (status != null)
                ? orderRepository.findByUserIdAndStatus(userId, status, pageable)
                : orderRepository.findByUserId(userId, pageable);
        return PageResponse.of(page, OrderMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<OrderResponse> getAllOrders(OrderStatus status, Pageable pageable) {
        Page<Order> page = (status != null)
                ? orderRepository.findAllByStatus(status, pageable)
                : orderRepository.findAll(pageable);
        return PageResponse.of(page, OrderMapper::toResponse);
    }

    @Override
    @Transactional
    public OrderResponse updateStatus(Long id, UpdateOrderStatusRequest request, UserPrincipal principal) {
        Order order = requireOrder(id);
        OrderStatus previousStatus = order.getStatus();
        if (!previousStatus.canTransitionTo(request.getStatus())) {
            throw new InvalidOperationException(
                    "Cannot transition order from " + previousStatus + " to " + request.getStatus());
        }
        order.setStatus(request.getStatus());
        log.info("Order {} status changed from {} to {}", order.getOrderNumber(), previousStatus, request.getStatus());
        eventPublisher.publishEvent(new OrderUpdatedEvent(this, order, previousStatus));
        return OrderMapper.toResponse(order);
    }

    @Override
    @Transactional
    public OrderResponse cancelOrder(Long id, CancelOrderRequest request, UserPrincipal principal) {
        Order order = requireOrder(id);

        boolean isOwner = order.getUserId().equals(principal.getId());
        boolean isAdmin = principal.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        if (!isOwner && !isAdmin) {
            throw new ForbiddenException("You do not have access to this order");
        }
        if (!order.getStatus().isCancellable()) {
            throw new InvalidOperationException(
                    "Order in status " + order.getStatus() + " cannot be cancelled");
        }

        List<StockItemRequest> stockItems = order.getItems().stream()
                .map(i -> new StockItemRequest(i.getProductId(), i.getQuantity()))
                .collect(Collectors.toList());
        productClient.releaseStock(new StockOperationRequest(order.getOrderNumber(), stockItems));

        order.setStatus(OrderStatus.CANCELLED);
        order.setCancelledAt(LocalDateTime.now());
        order.setCancellationReason(request != null ? request.getReason() : null);

        log.info("Order {} cancelled by userId={}", order.getOrderNumber(), principal.getId());
        eventPublisher.publishEvent(new OrderCancelledEvent(this, order));
        return OrderMapper.toResponse(order);
    }

    private Order requireOrder(Long id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "id", id));
    }

    private String generateOrderNumber() {
        String date = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        String suffix = String.format("%04d", System.nanoTime() % 10000);
        return "ORD-" + date + "-" + suffix;
    }
}
