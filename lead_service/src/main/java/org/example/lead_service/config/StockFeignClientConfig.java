package org.example.lead_service.config;

import feign.RequestInterceptor;
import org.example.lead_service.security.ServiceTokenProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpHeaders;

/** Feign credentials dedicated to the lead-service -> stock-service boundary. */
public class StockFeignClientConfig {

    @Bean
    RequestInterceptor stockServiceTokenInterceptor(ServiceTokenProvider tokenProvider) {
        return template -> {
            template.removeHeader(HttpHeaders.AUTHORIZATION);
            template.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenProvider.forCurrentRequest());
        };
    }
}
