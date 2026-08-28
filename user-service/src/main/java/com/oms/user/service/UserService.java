package com.oms.user.service;

import com.oms.common.dto.PageResponse;
import com.oms.user.dto.AddressRequest;
import com.oms.user.dto.AddressResponse;
import com.oms.user.dto.InternalUserResponse;
import com.oms.user.dto.UpdateUserRequest;
import com.oms.user.dto.UserResponse;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface UserService {

    UserResponse getById(Long id);

    UserResponse getByEmail(String email);

    PageResponse<UserResponse> search(String keyword, Pageable pageable);

    UserResponse update(Long id, UpdateUserRequest request);

    void deactivate(Long id);

    List<AddressResponse> getAddresses(Long userId);

    AddressResponse addAddress(Long userId, AddressRequest request);

    void deleteAddress(Long userId, Long addressId);

    InternalUserResponse getInternal(Long id);
}
