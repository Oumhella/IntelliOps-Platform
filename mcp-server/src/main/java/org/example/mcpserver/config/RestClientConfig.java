package org.example.mcpserver.config;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import org.springframework.http.HttpRequest;

@Configuration
public class RestClientConfig {

    @Value("${services.gateway.url}")
    private String gatewayUrl;

    @Bean
    public RestClient gatewayClient() {
        return RestClient.builder()
                .baseUrl(gatewayUrl)
                .requestInterceptor((request, body, execution) -> {
                    propagateHeaders(request);
                    return execution.execute(request, body);
                })
                .build();
    }

    @Bean
    public RestClient leadServiceClient(@Qualifier("gatewayClient") RestClient gatewayClient) {
        return gatewayClient;
    }

    @Bean
    public RestClient stockServiceClient(@Qualifier("gatewayClient") RestClient gatewayClient) {
        return gatewayClient;
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
