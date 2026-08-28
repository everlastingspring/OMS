package com.oms.order.controller;

import com.oms.common.dto.ApiResponse;
import com.oms.common.dto.PageResponse;
import com.oms.common.security.UserPrincipal;
import com.oms.order.dto.CancelOrderRequest;
import com.oms.order.dto.CreateOrderRequest;
import com.oms.order.dto.OrderResponse;
import com.oms.order.dto.UpdateOrderStatusRequest;
import com.oms.order.entity.OrderStatus;
import com.oms.order.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Orders", description = "Order lifecycle management")
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    @Operation(summary = "Place an order")
    public ResponseEntity<ApiResponse<OrderResponse>> create(
            @Valid @RequestBody CreateOrderRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        OrderResponse created = orderService.createOrder(request, principal);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(created, "Order placed"));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get an order by id (owner or ADMIN)")
    public ResponseEntity<ApiResponse<OrderResponse>> getById(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success(orderService.getOrder(id, principal)));
    }

    @GetMapping
    @Operation(summary = "Get my orders (paged, optionally filtered by status)")
    public ResponseEntity<ApiResponse<PageResponse<OrderResponse>>> getMyOrders(
            @RequestParam(required = false) OrderStatus status,
            @AuthenticationPrincipal UserPrincipal principal,
            @PageableDefault(size = 20, sort = "placedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(
                orderService.getMyOrders(principal.getId(), status, pageable)));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update order status (ADMIN only) — PENDING→CONFIRMED→SHIPPED→DELIVERED")
    public ResponseEntity<ApiResponse<OrderResponse>> updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateOrderStatusRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success(
                orderService.updateStatus(id, request, principal), "Order status updated"));
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Cancel an order (owner or ADMIN, PENDING or CONFIRMED only)")
    public ResponseEntity<ApiResponse<OrderResponse>> cancel(
            @PathVariable Long id,
            @RequestBody(required = false) CancelOrderRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success(
                orderService.cancelOrder(id, request, principal), "Order cancelled"));
    }
}
