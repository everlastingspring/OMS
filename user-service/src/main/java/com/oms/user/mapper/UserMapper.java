package com.oms.user.mapper;

import com.oms.user.dto.AddressRequest;
import com.oms.user.dto.AddressResponse;
import com.oms.user.dto.InternalUserResponse;
import com.oms.user.dto.UserResponse;
import com.oms.user.entity.Address;
import com.oms.user.entity.User;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Hand-written on purpose. MapStruct would generate this, but an extra
 * annotation processor alongside Lombok is a common source of build breakage
 * and buys very little for six fields.
 */
public final class UserMapper {

    private UserMapper() {
    }

    public static UserResponse toResponse(User user) {
        return toResponse(user, false);
    }

    public static UserResponse toResponse(User user, boolean includeAddresses) {
        UserResponse response = new UserResponse();
        response.setId(user.getId());
        response.setName(user.getName());
        response.setEmail(user.getEmail());
        response.setPhone(user.getPhone());
        response.setRole(user.getRole().name());
        response.setActive(user.isActive());
        response.setCreatedAt(user.getCreatedAt());
        if (includeAddresses) {
            response.setAddresses(toAddressResponses(user.getAddresses()));
        }
        return response;
    }

    public static InternalUserResponse toInternalResponse(User user) {
        InternalUserResponse response = new InternalUserResponse();
        response.setId(user.getId());
        response.setName(user.getName());
        response.setEmail(user.getEmail());
        response.setActive(user.isActive());
        user.getAddresses().stream()
                .filter(Address::isDefaultAddress)
                .findFirst()
                .ifPresent(address -> response.setDefaultShippingAddress(address.toSingleLine()));
        return response;
    }

    public static List<AddressResponse> toAddressResponses(List<Address> addresses) {
        return addresses.stream().map(UserMapper::toAddressResponse).collect(Collectors.toList());
    }

    public static AddressResponse toAddressResponse(Address address) {
        AddressResponse response = new AddressResponse();
        response.setId(address.getId());
        response.setLabel(address.getLabel());
        response.setLine1(address.getLine1());
        response.setLine2(address.getLine2());
        response.setCity(address.getCity());
        response.setState(address.getState());
        response.setPostalCode(address.getPostalCode());
        response.setCountry(address.getCountry());
        response.setDefaultAddress(address.isDefaultAddress());
        return response;
    }

    public static Address toEntity(AddressRequest request) {
        Address address = new Address();
        address.setLabel(request.getLabel());
        address.setLine1(request.getLine1());
        address.setLine2(request.getLine2());
        address.setCity(request.getCity());
        address.setState(request.getState());
        address.setPostalCode(request.getPostalCode());
        address.setCountry(request.getCountry());
        address.setDefaultAddress(request.isDefaultAddress());
        return address;
    }
}
