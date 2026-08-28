package com.oms.order.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oms.common.exception.GlobalExceptionHandler;
import com.oms.common.exception.ResourceNotFoundException;
import com.oms.common.security.UserPrincipal;
import com.oms.order.dto.CancelOrderRequest;
import com.oms.order.dto.CreateOrderRequest;
import com.oms.order.dto.OrderItemRequest;
import com.oms.order.dto.OrderItemResponse;
import com.oms.order.dto.OrderResponse;
import com.oms.order.dto.UpdateOrderStatusRequest;
import com.oms.order.entity.OrderStatus;
import com.oms.order.service.OrderService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderController")
class OrderControllerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Mock
    private OrderService orderService;

    private MockMvc mockMvc;
    private UserPrincipal userPrincipal;
    private UserPrincipal adminPrincipal;

    @BeforeEach
    void setUp() {
        userPrincipal = new UserPrincipal(2L, "priya@oms.com", "USER");
        adminPrincipal = new UserPrincipal(1L, "admin@oms.com", "ADMIN");

        mockMvc = MockMvcBuilders.standaloneSetup(new OrderController(orderService))
                .setCustomArgumentResolvers(
                        new PageableHandlerMethodArgumentResolver(),
                        new AuthenticationPrincipalArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private RequestPostProcessor asPrincipal(UserPrincipal principal) {
        return request -> {
            SecurityContext ctx = SecurityContextHolder.createEmptyContext();
            ctx.setAuthentication(new UsernamePasswordAuthenticationToken(
                    principal, null, principal.getAuthorities()));
            SecurityContextHolder.setContext(ctx);
            return request;
        };
    }

    private OrderResponse sampleResponse() {
        OrderResponse response = new OrderResponse();
        response.setId(1L);
        response.setOrderNumber("ORD-20260828-0001");
        response.setUserId(2L);
        response.setStatus(OrderStatus.PENDING);
        response.setTotalAmount(new BigDecimal("200.00"));
        response.setShippingAddress("123 Main St");
        response.setPlacedAt(LocalDateTime.now());

        OrderItemResponse item = new OrderItemResponse();
        item.setId(1L);
        item.setProductId(1L);
        item.setSku("ELEC-001");
        item.setProductName("Widget");
        item.setQuantity(2);
        item.setUnitPrice(new BigDecimal("100.00"));
        item.setLineTotal(new BigDecimal("200.00"));
        response.setItems(Collections.singletonList(item));
        return response;
    }

    @Test
    @DisplayName("POST /orders returns 201 for a valid request")
    void create_valid_returns201() throws Exception {
        when(orderService.createOrder(any(CreateOrderRequest.class), any()))
                .thenReturn(sampleResponse());

        OrderItemRequest item = new OrderItemRequest();
        item.setProductId(1L);
        item.setQuantity(2);

        CreateOrderRequest request = new CreateOrderRequest();
        request.setItems(Collections.singletonList(item));
        request.setShippingAddress("123 Main St");

        mockMvc.perform(post("/api/v1/orders")
                        .with(asPrincipal(userPrincipal))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(MAPPER.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.orderNumber").value("ORD-20260828-0001"))
                .andExpect(jsonPath("$.data.status").value("PENDING"));
    }

    @Test
    @DisplayName("POST /orders returns 400 when items list is empty")
    void create_emptyItems_returns400() throws Exception {
        CreateOrderRequest request = new CreateOrderRequest();
        request.setItems(Collections.emptyList());
        request.setShippingAddress("123 Main St");

        mockMvc.perform(post("/api/v1/orders")
                        .with(asPrincipal(userPrincipal))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(MAPPER.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("GET /orders/{id} returns 200 for the order owner")
    void getById_owner_returns200() throws Exception {
        when(orderService.getOrder(eq(1L), any())).thenReturn(sampleResponse());

        mockMvc.perform(get("/api/v1/orders/1")
                        .with(asPrincipal(userPrincipal)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(1));
    }

    @Test
    @DisplayName("GET /orders/{id} returns 404 for unknown order")
    void getById_unknown_returns404() throws Exception {
        when(orderService.getOrder(eq(999L), any()))
                .thenThrow(new ResourceNotFoundException("Order", "id", 999L));

        mockMvc.perform(get("/api/v1/orders/999")
                        .with(asPrincipal(userPrincipal)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("POST /orders/{id}/cancel returns 200")
    void cancel_returns200() throws Exception {
        OrderResponse cancelled = sampleResponse();
        cancelled.setStatus(OrderStatus.CANCELLED);
        when(orderService.cancelOrder(eq(1L), any(), any())).thenReturn(cancelled);

        CancelOrderRequest cancelRequest = new CancelOrderRequest();
        cancelRequest.setReason("Changed my mind");

        mockMvc.perform(post("/api/v1/orders/1/cancel")
                        .with(asPrincipal(userPrincipal))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(MAPPER.writeValueAsString(cancelRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));
    }

    @Test
    @DisplayName("PATCH /orders/{id}/status returns 200 for admin")
    void updateStatus_admin_returns200() throws Exception {
        OrderResponse confirmed = sampleResponse();
        confirmed.setStatus(OrderStatus.CONFIRMED);
        when(orderService.updateStatus(eq(1L), any(UpdateOrderStatusRequest.class), any()))
                .thenReturn(confirmed);

        UpdateOrderStatusRequest req = new UpdateOrderStatusRequest();
        req.setStatus(OrderStatus.CONFIRMED);

        mockMvc.perform(patch("/api/v1/orders/1/status")
                        .with(asPrincipal(adminPrincipal))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(MAPPER.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CONFIRMED"));
    }
}
