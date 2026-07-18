package org.example.common.feign;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Propage automatiquement le header Authorization de la requete entrante
 * vers tout appel Feign sortant, pour n'importe quel microservice qui
 * inclut common_lib. Applique globalement a tous les clients Feign du
 * service (Spring Cloud OpenFeign applique tout bean RequestInterceptor
 * present dans le contexte a tous les @FeignClient, sans configuration
 * supplementaire par client).
 */
public class FeignClientConfig implements RequestInterceptor {

    @Override
    public void apply(RequestTemplate template) {
        ServletRequestAttributes requestAttributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();

        if (requestAttributes == null) {
            return;
        }

        HttpServletRequest request = requestAttributes.getRequest();
        String authorizationHeader = request.getHeader(HttpHeaders.AUTHORIZATION);

        if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
            template.header(HttpHeaders.AUTHORIZATION, authorizationHeader);
        }
    }
}