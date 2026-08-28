package com.oms.user.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

/** Note the absence of a password field. The hash never leaves the service. */
@Getter
@Setter
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserResponse {
    private Long id;
    private String name;
    private String email;
    private String phone;
    private String role;
    private boolean active;
    private LocalDateTime createdAt;
    private List<AddressResponse> addresses;
}
