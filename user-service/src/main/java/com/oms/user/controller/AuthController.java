package com.oms.user.controller;

import com.oms.common.dto.ApiResponse;
import com.oms.user.dto.AuthResponse;
import com.oms.user.dto.LoginRequest;
import com.oms.user.dto.RegisterRequest;
import com.oms.user.dto.UserResponse;
import com.oms.user.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Registration and token issuance")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    @Operation(summary = "Register a new user",
            description = "Creates an account with ROLE_USER and a BCrypt-hashed password. "
                    + "Returns 409 if the email is already registered.")
    public ResponseEntity<ApiResponse<UserResponse>> register(@Valid @RequestBody RegisterRequest request) {
        UserResponse created = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(created, "Registration successful"));
    }

    @PostMapping("/login")
    @Operation(summary = "Log in and receive a JWT",
            description = "Returns 401 for a wrong password, an unknown email or a deactivated account. "
                    + "The three cases share one message so the endpoint cannot be used to enumerate users.")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(ApiResponse.success(response, "Login successful"));
    }
}
