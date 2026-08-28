package com.oms.common.security;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "oms.jwt")
public class JwtProperties {

    /** HMAC-SHA256 signing secret. Must be at least 32 bytes. Injected from the environment in Docker. */
    private String secret = "change-me-this-is-a-development-only-secret-key-32b";

    /** Access token lifetime in milliseconds. Default one hour. */
    private long expirationMs = 3_600_000L;

    private String issuer = "oms";
}
