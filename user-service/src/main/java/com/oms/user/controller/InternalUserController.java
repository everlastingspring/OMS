package com.oms.user.controller;

import com.oms.common.dto.ApiResponse;
import com.oms.user.dto.InternalUserResponse;
import com.oms.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Service-to-service only. Guarded by InternalApiKeyInterceptor, which requires
 * the X-Internal-Api-Key header. Not routed from outside the cluster.
 */
@RestController
@RequestMapping("/api/v1/internal/users")
@RequiredArgsConstructor
@Tag(name = "Internal", description = "Service-to-service endpoints, not for public clients")
public class InternalUserController {

    private final UserService userService;

    @GetMapping("/{id}")
    @Operation(summary = "Resolve a user for order-service",
            description = "Returns existence, active flag and the default shipping address.")
    public ResponseEntity<ApiResponse<InternalUserResponse>> getUser(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(userService.getInternal(id)));
    }
}
