package com.oms.common.security;

import com.oms.common.constant.OmsConstants;
import com.oms.common.exception.UnauthorizedException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Guards /api/v1/internal/** with a shared secret. These endpoints exist for
 * service-to-service calls only and are never routed from outside the cluster;
 * the header check is defence in depth, not the primary control.
 */
@Slf4j
@Component
public class InternalApiKeyInterceptor implements HandlerInterceptor {

    private final String expectedKey;

    public InternalApiKeyInterceptor(@Value("${oms.internal.api-key}") String expectedKey) {
        this.expectedKey = expectedKey;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String provided = request.getHeader(OmsConstants.INTERNAL_API_KEY_HEADER);
        if (expectedKey.equals(provided)) {
            return true;
        }
        log.warn("Rejected internal call to {} {} - missing or wrong {}",
                request.getMethod(), request.getRequestURI(), OmsConstants.INTERNAL_API_KEY_HEADER);
        throw new UnauthorizedException("A valid " + OmsConstants.INTERNAL_API_KEY_HEADER
                + " header is required for internal endpoints");
    }
}
