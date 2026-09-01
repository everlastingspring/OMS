package com.oms.audit.controller;

import com.oms.audit.document.ActivityHistory;
import com.oms.audit.document.OrderAuditLog;
import com.oms.audit.service.AuditService;
import com.oms.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/audit")
@RequiredArgsConstructor
@Tag(name = "Audit", description = "Order audit trail and user activity history")
@SecurityRequirement(name = "bearerAuth")
public class AuditController {

    private final AuditService auditService;

    @GetMapping("/orders/{orderId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get full audit trail for an order (ADMIN only)",
            description = "Returns every recorded event for the given order, newest first.")
    public ResponseEntity<ApiResponse<List<OrderAuditLog>>> getOrderAuditTrail(@PathVariable Long orderId) {
        return ResponseEntity.ok(ApiResponse.success(auditService.getAuditTrailForOrder(orderId)));
    }

    @GetMapping("/users/{userId}/activity")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get activity history for a user (ADMIN only)",
            description = "Returns all order-related activity for the given user, newest first.")
    public ResponseEntity<ApiResponse<List<ActivityHistory>>> getUserActivity(@PathVariable Long userId) {
        return ResponseEntity.ok(ApiResponse.success(auditService.getActivityForUser(userId)));
    }
}
