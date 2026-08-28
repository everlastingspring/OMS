package com.oms.user.service.impl;

import com.oms.common.exception.DuplicateResourceException;
import com.oms.common.exception.UnauthorizedException;
import com.oms.common.security.JwtTokenProvider;
import com.oms.user.dto.AuthResponse;
import com.oms.user.dto.LoginRequest;
import com.oms.user.dto.RegisterRequest;
import com.oms.user.dto.UserResponse;
import com.oms.user.entity.Role;
import com.oms.user.entity.User;
import com.oms.user.mapper.UserMapper;
import com.oms.user.repository.UserRepository;
import com.oms.user.service.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private static final String BEARER = "Bearer";
    private static final String INVALID_CREDENTIALS = "Invalid email or password";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;

    @Override
    @Transactional
    public UserResponse register(RegisterRequest request) {
        String email = normalise(request.getEmail());
        if (userRepository.existsByEmail(email)) {
            throw new DuplicateResourceException("User", "email", email);
        }

        User user = new User();
        user.setName(request.getName().trim());
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setPhone(emptyToNull(request.getPhone()));
        // Registration always creates a plain USER. There is deliberately no
        // API that grants ADMIN - those are seeded or promoted by a DBA.
        user.setRole(Role.USER);
        user.setActive(true);

        User saved = userRepository.save(user);
        log.info("Registered user id={} email={}", saved.getId(), saved.getEmail());
        return UserMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        String email = normalise(request.getEmail());

        // Same message and same code path for "no such user" and "wrong password"
        // so the endpoint cannot be used to enumerate registered addresses.
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> {
                    log.warn("Login attempt for unknown email: {}", email);
                    return new UnauthorizedException(INVALID_CREDENTIALS);
                });

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            log.warn("Login failed - wrong password for user id={}", user.getId());
            throw new UnauthorizedException(INVALID_CREDENTIALS);
        }

        if (!user.isActive()) {
            log.warn("Login blocked - user id={} is deactivated", user.getId());
            throw new UnauthorizedException("This account has been deactivated");
        }

        String token = tokenProvider.generateToken(user.getId(), user.getEmail(), user.getRole().name());
        log.info("Issued token for user id={} role={}", user.getId(), user.getRole());
        return new AuthResponse(token, BEARER, tokenProvider.getExpirationMs(), UserMapper.toResponse(user));
    }

    private String normalise(String email) {
        return email == null ? null : email.trim().toLowerCase();
    }

    private String emptyToNull(String value) {
        return (value == null || value.trim().isEmpty()) ? null : value.trim();
    }
}
