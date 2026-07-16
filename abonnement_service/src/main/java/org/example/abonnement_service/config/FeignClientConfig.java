package org.example.abonnement_service.config;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Configuration
public class FeignClientConfig {

    @Bean
    public RequestInterceptor requestTokenBearerInterceptor() {
        return new RequestInterceptor() {
            @Override
            public void apply(RequestTemplate template) {
                ServletRequestAttributes requestAttributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();

                if (requestAttributes != null) {
                    HttpServletRequest request = requestAttributes.getRequest();

                    // Récupération directe de l'en-tête HTTP brut
                    String authorizationHeader = request.getHeader(HttpHeaders.AUTHORIZATION);

                    System.out.println("====== DEBUG FEIGN ======");
                    System.out.println("Requête d'origine sur : " + request.getRequestURI());
                    System.out.println("Header Authorization trouvé : " + (authorizationHeader != null ? "OUI" : "NON (NULL)"));
                    if (authorizationHeader != null) {
                        System.out.println("Valeur du Header : " + authorizationHeader.substring(0, Math.min(authorizationHeader.length(), 20)) + "...");
                    }
                    System.out.println("=========================");

                    // Injection dans la requête sortante vers user-service
                    if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
                        template.header(HttpHeaders.AUTHORIZATION, authorizationHeader);
                    }
                } else {
                    System.out.println("====== DEBUG FEIGN : requestAttributes est NULL ======");
                }
            }
        };
    }
}