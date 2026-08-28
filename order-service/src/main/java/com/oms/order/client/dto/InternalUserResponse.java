package com.oms.order.client.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class InternalUserResponse {

    private Long id;
    private String email;
    private String firstName;
    private String lastName;
    private String phone;
    private String role;
    private boolean active;
}
