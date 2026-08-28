package com.oms.common.constant;

public final class OmsConstants {

    private OmsConstants() {
    }

    public static final String CORRELATION_ID_HEADER = "X-Correlation-Id";
    public static final String CORRELATION_ID_MDC_KEY = "correlationId";
    public static final String INTERNAL_API_KEY_HEADER = "X-Internal-Api-Key";

    public static final String AUTH_HEADER = "Authorization";
    public static final String BEARER_PREFIX = "Bearer ";

    public static final String CLAIM_USER_ID = "uid";
    public static final String CLAIM_ROLE = "role";
    public static final String ROLE_PREFIX = "ROLE_";

    public static final String INTERNAL_PATH_PATTERN = "/api/v1/internal/**";
}
