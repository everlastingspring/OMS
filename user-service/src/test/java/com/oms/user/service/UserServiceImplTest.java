package com.oms.user.service;

import com.oms.common.exception.InvalidOperationException;
import com.oms.common.exception.ResourceNotFoundException;
import com.oms.user.dto.AddressRequest;
import com.oms.user.dto.AddressResponse;
import com.oms.user.dto.UpdateUserRequest;
import com.oms.user.entity.Address;
import com.oms.user.entity.Role;
import com.oms.user.entity.User;
import com.oms.user.repository.AddressRepository;
import com.oms.user.repository.UserRepository;
import com.oms.user.service.impl.UserServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserService")
class UserServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private AddressRepository addressRepository;

    @InjectMocks
    private UserServiceImpl userService;

    private User user;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(2L);
        user.setName("Priya Nair");
        user.setEmail("priya@oms.com");
        user.setPassword("$2a$10$hashed");
        user.setRole(Role.USER);
        user.setActive(true);
    }

    private AddressRequest addressRequest(boolean asDefault) {
        AddressRequest request = new AddressRequest();
        request.setLabel("HOME");
        request.setLine1("14, 3rd Cross, Indiranagar");
        request.setCity("Bengaluru");
        request.setState("Karnataka");
        request.setPostalCode("560038");
        request.setCountry("India");
        request.setDefaultAddress(asDefault);
        return request;
    }

    @Test
    @DisplayName("returns 404 semantics for an unknown user id")
    void getById_unknownId_throwsNotFound() {
        when(userRepository.findByIdWithAddresses(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getById(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    @DisplayName("never exposes the password hash in the response")
    void getById_responseHasNoPasswordField() {
        when(userRepository.findByIdWithAddresses(2L)).thenReturn(Optional.of(user));

        assertThat(userService.getById(2L))
                .extracting("email", "role")
                .containsExactly("priya@oms.com", "USER");
    }

    @Test
    @DisplayName("makes the very first saved address the default, even when not asked")
    void addAddress_firstAddressBecomesDefault() {
        when(userRepository.findByIdWithAddresses(2L)).thenReturn(Optional.of(user));
        when(addressRepository.save(any(Address.class))).thenAnswer(invocation -> {
            Address toSave = invocation.getArgument(0);
            toSave.setId(1L);
            return toSave;
        });

        AddressResponse response = userService.addAddress(2L, addressRequest(false));

        assertThat(response.isDefaultAddress()).isTrue();
        assertThat(user.getAddresses()).hasSize(1);
    }

    @Test
    @DisplayName("demotes the previous default when a new default is added")
    void addAddress_newDefaultDemotesPrevious() {
        Address existing = new Address();
        existing.setId(1L);
        existing.setDefaultAddress(true);
        user.addAddress(existing);

        when(userRepository.findByIdWithAddresses(2L)).thenReturn(Optional.of(user));
        when(addressRepository.save(any(Address.class))).thenAnswer(invocation -> {
            Address toSave = invocation.getArgument(0);
            toSave.setId(2L);
            return toSave;
        });

        AddressResponse response = userService.addAddress(2L, addressRequest(true));

        assertThat(response.isDefaultAddress()).isTrue();
        assertThat(existing.isDefaultAddress()).isFalse();
    }

    @Test
    @DisplayName("caps the number of saved addresses per user")
    void addAddress_overLimit_throwsInvalidOperation() {
        for (int i = 0; i < 10; i++) {
            Address filler = new Address();
            filler.setId((long) i);
            user.addAddress(filler);
        }
        when(userRepository.findByIdWithAddresses(2L)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> userService.addAddress(2L, addressRequest(false)))
                .isInstanceOf(InvalidOperationException.class)
                .hasMessageContaining("at most 10");
    }

    @Test
    @DisplayName("deactivation is a soft delete and is not repeatable")
    void deactivate_alreadyInactive_throwsInvalidOperation() {
        user.setActive(false);
        when(userRepository.findById(2L)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> userService.deactivate(2L))
                .isInstanceOf(InvalidOperationException.class)
                .hasMessageContaining("already deactivated");
    }

    @Test
    @DisplayName("update changes name and phone but leaves email and role alone")
    void update_doesNotTouchEmailOrRole() {
        when(userRepository.findById(2L)).thenReturn(Optional.of(user));

        UpdateUserRequest request = new UpdateUserRequest();
        request.setName("Priya R Nair");
        request.setPhone("9880000009");

        userService.update(2L, request);

        assertThat(user.getName()).isEqualTo("Priya R Nair");
        assertThat(user.getPhone()).isEqualTo("9880000009");
        assertThat(user.getEmail()).isEqualTo("priya@oms.com");
        assertThat(user.getRole()).isEqualTo(Role.USER);
    }

    @Test
    @DisplayName("blank phone is normalised to null rather than an empty string")
    void update_blankPhoneBecomesNull() {
        when(userRepository.findById(2L)).thenReturn(Optional.of(user));

        UpdateUserRequest request = new UpdateUserRequest();
        request.setName("Priya Nair");
        request.setPhone("   ");

        userService.update(2L, request);

        assertThat(user.getPhone()).isNull();
    }
}
