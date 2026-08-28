package com.oms.common.config;

import com.oms.common.constant.OmsConstants;
import com.oms.common.security.InternalApiKeyInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class InternalApiWebConfig implements WebMvcConfigurer {

    private final InternalApiKeyInterceptor internalApiKeyInterceptor;

    public InternalApiWebConfig(InternalApiKeyInterceptor internalApiKeyInterceptor) {
        this.internalApiKeyInterceptor = internalApiKeyInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(internalApiKeyInterceptor)
                .addPathPatterns(OmsConstants.INTERNAL_PATH_PATTERN);
    }
}
