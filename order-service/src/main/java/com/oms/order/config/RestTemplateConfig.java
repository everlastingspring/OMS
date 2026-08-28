package com.oms.order.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.util.Collections;

@Configuration
public class RestTemplateConfig {

    @Value("${oms.internal.api-key}")
    private String internalApiKey;

    @Bean
    public RestTemplate internalRestTemplate() {
        RestTemplate restTemplate = new RestTemplate();
        restTemplate.setInterceptors(Collections.singletonList(new InternalApiKeyInterceptor(internalApiKey)));
        return restTemplate;
    }

    private static class InternalApiKeyInterceptor implements ClientHttpRequestInterceptor {

        private final String apiKey;

        InternalApiKeyInterceptor(String apiKey) {
            this.apiKey = apiKey;
        }

        @Override
        public ClientHttpResponse intercept(HttpRequest request, byte[] body,
                                            ClientHttpRequestExecution execution) throws IOException {
            request.getHeaders().set("X-Internal-Api-Key", apiKey);
            return execution.execute(request, body);
        }
    }
}
