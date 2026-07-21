package org.example.mcpserver.config;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import org.springframework.http.HttpRequest;

@Configuration
public class RestClientConfig {

    @Value("${services.lead-service.url}")
    private String leadServiceUrl;

    @Value("${services.stock-service.url}")
    private String stockServiceUrl;

    @Bean
    public RestClient leadServiceClient() {
        return RestClient.builder()
                .baseUrl(leadServiceUrl)
                .requestInterceptor((request, body, execution) -> {
                    propagateHeaders(request);
                    return execution.execute(request, body);
                })
                .build();
    }

    @Bean
    public RestClient stockServiceClient() {
        return RestClient.builder()
                .baseUrl(stockServiceUrl)
                .requestInterceptor((request, body, execution) -> {
                    propagateHeaders(request);
                    return execution.execute(request, body);
                })
                .build();
    }

    private void propagateHeaders(HttpRequest request) {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes != null) {
            HttpServletRequest currentRequest = attributes.getRequest();
            String userId = currentRequest.getHeader("X-User-Id");
            String authHeader = currentRequest.getHeader("Authorization");

            if (userId != null) {
                request.getHeaders().add("X-User-Id", userId);
            }
            if (authHeader != null) {
                request.getHeaders().add("Authorization", authHeader);
            }
        }
    }
}