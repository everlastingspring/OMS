package com.oms.audit.controller;

import com.oms.audit.document.ActivityHistory;
import com.oms.audit.document.OrderAuditLog;
import com.oms.audit.service.AuditService;
import com.oms.common.event.EventType;
import com.oms.common.exception.GlobalExceptionHandler;
import com.oms.common.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuditController")
class AuditControllerTest {

    @Mock
    private AuditService auditService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new AuditController(auditService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private OrderAuditLog sampleAuditLog(Long orderId) {
        OrderAuditLog log = new OrderAuditLog();
        log.setEventId("evt-001");
        log.setEventType(EventType.ORDER_CREATED);
        log.setOrderId(orderId);
        log.setOrderNumber("ORD-001");
        log.setUserId(2L);
        log.setTotalAmount(new BigDecimal("500.00"));
        log.setCurrentStatus("PENDING");
        log.setOccurredAt(Instant.now());
        return log;
    }

    private ActivityHistory sampleActivity(Long userId) {
        ActivityHistory activity = new ActivityHistory();
        activity.setUserId(userId);
        activity.setAction("ORDER_CREATED");
        activity.setEntityType("ORDER");
        activity.setEntityId(1L);
        activity.setOrderNumber("ORD-001");
        activity.setDetail("Order ORD-001 status: PENDING");
        return activity;
    }

    @Test
    @DisplayName("GET /audit/orders/{orderId} returns 200 with the audit trail list")
    void getOrderAuditTrail_existingOrder_returns200WithList() throws Exception {
        when(auditService.getAuditTrailForOrder(1L))
                .thenReturn(Arrays.asList(sampleAuditLog(1L), sampleAuditLog(1L)));

        mockMvc.perform(get("/api/v1/audit/orders/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].eventId").value("evt-001"))
                .andExpect(jsonPath("$.data[0].orderId").value(1))
                .andExpect(jsonPath("$.data[0].currentStatus").value("PENDING"));
    }

    @Test
    @DisplayName("GET /audit/orders/{orderId} returns 200 with empty list when no events recorded")
    void getOrderAuditTrail_noEvents_returns200WithEmptyList() throws Exception {
        when(auditService.getAuditTrailForOrder(99L))
                .thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/v1/audit/orders/99"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    @DisplayName("GET /audit/users/{userId}/activity returns 200 with the activity list")
    void getUserActivity_existingUser_returns200WithList() throws Exception {
        when(auditService.getActivityForUser(2L))
                .thenReturn(Arrays.asList(sampleActivity(2L), sampleActivity(2L)));

        mockMvc.perform(get("/api/v1/audit/users/2/activity"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].userId").value(2))
                .andExpect(jsonPath("$.data[0].action").value("ORDER_CREATED"))
                .andExpect(jsonPath("$.data[0].entityType").value("ORDER"));
    }

    @Test
    @DisplayName("GET /audit/users/{userId}/activity returns 200 with empty list when user has no activity")
    void getUserActivity_noActivity_returns200WithEmptyList() throws Exception {
        when(auditService.getActivityForUser(999L))
                .thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/v1/audit/users/999/activity"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    @DisplayName("GET /audit/orders/{orderId} returns 404 when service throws ResourceNotFoundException")
    void getOrderAuditTrail_serviceThrowsNotFound_returns404() throws Exception {
        when(auditService.getAuditTrailForOrder(404L))
                .thenThrow(new ResourceNotFoundException("Order", "id", 404L));

        mockMvc.perform(get("/api/v1/audit/orders/404"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }
}
