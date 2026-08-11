package org.example.storeintegration.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.example.storeintegration.config.IntegrationProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

@Component
public class ServiceTokenProvider {
    private final byte[] secret;
    private final long ttlSeconds;
    public ServiceTokenProvider(@Value("${app.jwt.secret}") String secret, IntegrationProperties properties) {
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.ttlSeconds = Math.max(30, Math.min(properties.serviceTokenTtlSeconds(), 300));
    }
    public String forTenant(Long enterpriseId) {
        Instant now = Instant.now();
        return Jwts.builder().setSubject("store-integration-service")
                .claim("role", "ROLE_INTEGRATION_SERVICE").claim("enterpriseId", enterpriseId).claim("userId", 0L)
                .setIssuedAt(Date.from(now)).setExpiration(Date.from(now.plusSeconds(ttlSeconds)))
                .signWith(Keys.hmacShaKeyFor(secret), SignatureAlgorithm.HS256).compact();
    }
}
