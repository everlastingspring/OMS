package com.oms.user.service;

import com.oms.common.exception.DuplicateResourceException;
import com.oms.common.exception.UnauthorizedException;
import com.oms.common.security.JwtTokenProvider;
import com.oms.user.dto.AuthResponse;
import com.oms.user.dto.LoginRequest;
import com.oms.user.dto.RegisterRequest;
import com.oms.user.dto.UserResponse;
import com.oms.user.entity.Role;
import com.oms.user.entity.User;
import com.oms.user.repository.UserRepository;
import com.oms.user.service.impl.AuthServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService")
class AuthServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenProvider tokenProvider;

    @InjectMocks
    private AuthServiceImpl authService;

    private RegisterRequest registerRequest(String email) {
        RegisterRequest request = new RegisterRequest();
        request.setName("Priya Nair");
        request.setEmail(email);
        request.setPassword("User@123");
        request.setPhone("9880000002");
        return request;
    }

    private User existingUser(String rawEmail, String storedHash, boolean active) {
        User user = new User();
        user.setId(2L);
        user.setName("Priya Nair");
        user.setEmail(rawEmail);
        user.setPassword(storedHash);
        user.setRole(Role.USER);
        user.setActive(active);
        return user;
    }

    @Test
    @DisplayName("stores a hash, never the raw password, and always assigns ROLE_USER")
    void register_hashesPasswordAndForcesUserRole() {
        when(userRepository.existsByEmail("priya@oms.com")).thenReturn(false);
        when(passwordEncoder.encode("User@123")).thenReturn("$2a$10$hashed");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User toSave = invocation.getArgument(0);
            toSave.setId(2L);
            return toSave;
        });

        UserResponse response = authService.register(registerRequest("Priya@OMS.com "));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User saved = captor.getValue();

        assertThat(saved.getPassword()).isEqualTo("$2a$10$hashed");
        assertThat(saved.getPassword()).isNotEqualTo("User@123");
        assertThat(saved.getRole()).isEqualTo(Role.USER);
        assertThat(saved.getEmail()).isEqualTo("priya@oms.com");
        assertThat(response.getId()).isEqualTo(2L);
    }

    @Test
    @DisplayName("rejects an email that is already registered with 409")
    void register_duplicateEmail_throwsConflict() {
        when(userRepository.existsByEmail("priya@oms.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(registerRequest("priya@oms.com")))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("priya@oms.com");

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("issues a token carrying the user id, email and role")
    void login_validCredentials_returnsToken() {
        User user = existingUser("priya@oms.com", "$2a$10$hashed", true);
        when(userRepository.findByEmail("priya@oms.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("User@123", "$2a$10$hashed")).thenReturn(true);
        when(tokenProvider.generateToken(2L, "priya@oms.com", "USER")).thenReturn("jwt-token");
        when(tokenProvider.getExpirationMs()).thenReturn(3_600_000L);

        LoginRequest request = new LoginRequest();
        request.setEmail("priya@oms.com");
        request.setPassword("User@123");

        AuthResponse response = authService.login(request);

        assertThat(response.getAccessToken()).isEqualTo("jwt-token");
        assertThat(response.getTokenType()).isEqualTo("Bearer");
        assertThat(response.getExpiresInMs()).isEqualTo(3_600_000L);
        assertThat(response.getUser().getEmail()).isEqualTo("priya@oms.com");
    }

    @Test
    @DisplayName("gives the same message for a wrong password as for an unknown email")
    void login_wrongPassword_throwsUnauthorizedWithGenericMessage() {
        User user = existingUser("priya@oms.com", "$2a$10$hashed", true);
        when(userRepository.findByEmail("priya@oms.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong-password", "$2a$10$hashed")).thenReturn(false);

        LoginRequest request = new LoginRequest();
        request.setEmail("priya@oms.com");
        request.setPassword("wrong-password");

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Invalid email or password");

        verify(tokenProvider, never()).generateToken(anyLong(), anyString(), anyString());
    }

    @Test
    @DisplayName("does not leak that an email is unregistered")
    void login_unknownEmail_throwsUnauthorizedWithGenericMessage() {
        when(userRepository.findByEmail("nobody@oms.com")).thenReturn(Optional.empty());

        LoginRequest request = new LoginRequest();
        request.setEmail("nobody@oms.com");
        request.setPassword("User@123");

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Invalid email or password");
    }

    @Test
    @DisplayName("refuses a deactivated account even with the right password")
    void login_deactivatedUser_throwsUnauthorized() {
        User user = existingUser("priya@oms.com", "$2a$10$hashed", false);
        when(userRepository.findByEmail("priya@oms.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("User@123", "$2a$10$hashed")).thenReturn(true);

        LoginRequest request = new LoginRequest();
        request.setEmail("priya@oms.com");
        request.setPassword("User@123");

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("deactivated");

        verify(tokenProvider, never()).generateToken(anyLong(), anyString(), anyString());
    }
}
