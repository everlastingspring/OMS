package com.oms.user.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Trimmed projection for order-service. It needs to know the user exists,
 * is active, and where to ship - nothing else.
 */
@Getter
@Setter
@NoArgsConstructor
public class InternalUserResponse {
    private Long id;
    private String name;
    private String email;
    private boolean active;
    private String defaultShippingAddress;
}
