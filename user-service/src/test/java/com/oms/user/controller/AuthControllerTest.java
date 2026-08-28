package com.oms.user.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oms.common.exception.DuplicateResourceException;
import com.oms.common.exception.GlobalExceptionHandler;
import com.oms.common.exception.UnauthorizedException;
import com.oms.user.dto.AuthResponse;
import com.oms.user.dto.LoginRequest;
import com.oms.user.dto.RegisterRequest;
import com.oms.user.dto.UserResponse;
import com.oms.user.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Standalone MockMvc rather than @WebMvcTest: this exercises request mapping,
 * Bean Validation and the shared GlobalExceptionHandler without booting a
 * Spring context, so the test cannot be broken by unrelated auto-configuration.
 * Authorisation rules are a property of SecurityConfig and are not in scope here.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AuthController")
class AuthControllerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Mock
    private AuthService authService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new AuthController(authService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private RegisterRequest validRegisterRequest() {
        RegisterRequest request = new RegisterRequest();
        request.setName("Priya Nair");
        request.setEmail("priya@oms.com");
        request.setPassword("User@123");
        request.setPhone("9880000002");
        return request;
    }

    private UserResponse userResponse() {
        UserResponse response = new UserResponse();
        response.setId(2L);
        response.setName("Priya Nair");
        response.setEmail("priya@oms.com");
        response.setRole("USER");
        response.setActive(true);
        response.setCreatedAt(LocalDateTime.of(2026, 8, 27, 10, 0));
        return response;
    }

    @Test
    @DisplayName("POST /register returns 201 and the created user without a password")
    void register_valid_returns201() throws Exception {
        when(authService.register(any(RegisterRequest.class))).thenReturn(userResponse());

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(MAPPER.writeValueAsString(validRegisterRequest())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(2))
                .andExpect(jsonPath("$.data.email").value("priya@oms.com"))
                .andExpect(jsonPath("$.data.password").doesNotExist());
    }

    @Test
    @DisplayName("POST /register returns 400 and names the offending field for a bad email")
    void register_invalidEmail_returns400WithFieldError() throws Exception {
        RegisterRequest request = validRegisterRequest();
        request.setEmail("not-an-email");

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(MAPPER.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[0].field").value("email"));
    }

    @Test
    @DisplayName("POST /register returns 400 for a password with no digit or special character")
    void register_weakPassword_returns400() throws Exception {
        RegisterRequest request = validRegisterRequest();
        request.setPassword("passwordonly");

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(MAPPER.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("password"));
    }

    @Test
    @DisplayName("POST /register returns 409 when the email is taken")
    void register_duplicateEmail_returns409() throws Exception {
        when(authService.register(any(RegisterRequest.class)))
                .thenThrow(new DuplicateResourceException("User", "email", "priya@oms.com"));

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(MAPPER.writeValueAsString(validRegisterRequest())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("DUPLICATE_RESOURCE"));
    }

    @Test
    @DisplayName("POST /login returns 200 with a bearer token")
    void login_valid_returns200WithToken() throws Exception {
        when(authService.login(any(LoginRequest.class)))
                .thenReturn(new AuthResponse("jwt-token", "Bearer", 3_600_000L, userResponse()));

        LoginRequest request = new LoginRequest();
        request.setEmail("priya@oms.com");
        request.setPassword("User@123");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(MAPPER.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").value("jwt-token"))
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"));
    }

    @Test
    @DisplayName("POST /login returns 401 for bad credentials")
    void login_badCredentials_returns401() throws Exception {
        when(authService.login(any(LoginRequest.class)))
                .thenThrow(new UnauthorizedException("Invalid email or password"));

        LoginRequest request = new LoginRequest();
        request.setEmail("priya@oms.com");
        request.setPassword("wrong-password");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(MAPPER.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }

    @Test
    @DisplayName("POST /login returns 400 when the body is missing")
    void login_missingBody_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("MALFORMED_REQUEST"));
    }
}
