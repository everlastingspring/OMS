package com.oms.user.service.impl;

import com.oms.common.dto.PageResponse;
import com.oms.common.exception.InvalidOperationException;
import com.oms.common.exception.ResourceNotFoundException;
import com.oms.user.dto.AddressRequest;
import com.oms.user.dto.AddressResponse;
import com.oms.user.dto.InternalUserResponse;
import com.oms.user.dto.UpdateUserRequest;
import com.oms.user.dto.UserResponse;
import com.oms.user.entity.Address;
import com.oms.user.entity.User;
import com.oms.user.mapper.UserMapper;
import com.oms.user.repository.AddressRepository;
import com.oms.user.repository.UserRepository;
import com.oms.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private static final int MAX_ADDRESSES_PER_USER = 10;

    private final UserRepository userRepository;
    private final AddressRepository addressRepository;

    @Override
    @Transactional(readOnly = true)
    public UserResponse getById(Long id) {
        User user = userRepository.findByIdWithAddresses(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));
        return UserMapper.toResponse(user, true);
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse getByEmail(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", email));
        return UserMapper.toResponse(user, true);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<UserResponse> search(String keyword, Pageable pageable) {
        String safeKeyword = keyword == null ? "" : keyword.trim();
        Page<User> page = userRepository.search(safeKeyword, pageable);
        return PageResponse.of(page, UserMapper::toResponse);
    }

    @Override
    @Transactional
    public UserResponse update(Long id, UpdateUserRequest request) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));
        user.setName(request.getName().trim());
        user.setPhone(request.getPhone() == null || request.getPhone().trim().isEmpty()
                ? null : request.getPhone().trim());
        log.info("Updated profile for user id={}", id);
        return UserMapper.toResponse(user);
    }

    @Override
    @Transactional
    public void deactivate(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));
        if (!user.isActive()) {
            throw new InvalidOperationException("User is already deactivated");
        }
        user.setActive(false);
        log.info("Deactivated user id={}", id);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AddressResponse> getAddresses(Long userId) {
        requireUser(userId);
        return UserMapper.toAddressResponses(addressRepository.findByUserIdAndActiveTrue(userId));
    }

    @Override
    @Transactional
    public AddressResponse addAddress(Long userId, AddressRequest request) {
        User user = userRepository.findByIdWithAddresses(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        if (user.getAddresses().size() >= MAX_ADDRESSES_PER_USER) {
            throw new InvalidOperationException(
                    "A user may have at most " + MAX_ADDRESSES_PER_USER + " saved addresses");
        }

        Address address = UserMapper.toEntity(request);

        // The first address a user saves becomes the default whether they asked or not,
        // otherwise order-service would have nowhere to ship.
        boolean isFirst = user.getAddresses().isEmpty();
        if (isFirst || request.isDefaultAddress()) {
            // These are managed entities inside the transaction, so dirty checking
            // flushes the demotion. A bulk @Modifying update here would fight the
            // persistence context rather than help it.
            user.getAddresses().forEach(existing -> existing.setDefaultAddress(false));
            address.setDefaultAddress(true);
        }

        user.addAddress(address);
        Address saved = addressRepository.save(address);
        log.info("Added address id={} for user id={} (default={})", saved.getId(), userId, saved.isDefaultAddress());
        return UserMapper.toAddressResponse(saved);
    }

    @Override
    @Transactional
    public void deleteAddress(Long userId, Long addressId) {
        Address address = addressRepository.findByIdAndUserIdAndActiveTrue(addressId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Address", "id", addressId));
        address.setActive(false);
        log.info("Soft-deleted address id={} for user id={}", addressId, userId);
    }

    @Override
    @Transactional(readOnly = true)
    public InternalUserResponse getInternal(Long id) {
        User user = userRepository.findByIdWithAddresses(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));
        return UserMapper.toInternalResponse(user);
    }

    private void requireUser(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new ResourceNotFoundException("User", "id", userId);
        }
    }
}
