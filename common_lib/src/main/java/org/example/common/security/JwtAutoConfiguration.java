package org.example.common.security;

import org.example.common.feign.FeignClientConfig;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;

/**
 * Enregistre automatiquement JwtUtils, JwtAuthenticationFilter et
 * FeignClientConfig comme beans Spring des qu'un service ajoute la
 * dependance common_lib -- aucun @ComponentScan supplementaire requis
 * dans les classes @SpringBootApplication de chaque service.
 *
 * @ConditionalOnMissingBean permet a un service de fournir sa propre
 * implementation si besoin, sans conflit de bean.
 */
@AutoConfiguration
public class JwtAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public JwtUtils jwtUtils() {
        return new JwtUtils();
    }

    @Bean
    @ConditionalOnMissingBean
    public JwtAuthenticationFilter jwtAuthenticationFilter(JwtUtils jwtUtils) {
        return new JwtAuthenticationFilter(jwtUtils);
    }

    @Bean
    @ConditionalOnMissingBean
    public FeignClientConfig feignClientConfig() {
        return new FeignClientConfig();
    }
}