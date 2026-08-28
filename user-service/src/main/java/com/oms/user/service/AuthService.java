package com.oms.user.service;

import com.oms.user.dto.AuthResponse;
import com.oms.user.dto.LoginRequest;
import com.oms.user.dto.RegisterRequest;
import com.oms.user.dto.UserResponse;

public interface AuthService {

    UserResponse register(RegisterRequest request);

    AuthResponse login(LoginRequest request);
}
