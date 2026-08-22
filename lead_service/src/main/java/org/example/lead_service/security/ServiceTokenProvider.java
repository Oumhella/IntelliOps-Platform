package org.example.lead_service.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.example.common.security.TenantContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

/**
 * Issues short-lived credentials for calls owned by lead-service itself.
 * The originating tenant and user are retained so downstream stock movements
 * remain tenant-safe and auditable without granting the end-user stock roles.
 */
@Component
public class ServiceTokenProvider {

    private final byte[] secret;
    private final long ttlSeconds;

    public ServiceTokenProvider(
            @Value("${app.jwt.secret}") String secret,
            @Value("${lead.service-token-ttl-seconds:120}") long ttlSeconds) {
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.ttlSeconds = Math.max(30, Math.min(ttlSeconds, 300));
    }

    public String forCurrentRequest() {
        Instant now = Instant.now();
        return Jwts.builder()
                .setSubject("lead-service")
                .claim("role", "ROLE_INTEGRATION_SERVICE")
                .claim("enterpriseId", TenantContext.requireEnterpriseId())
                .claim("userId", TenantContext.requireUserId())
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(now.plusSeconds(ttlSeconds)))
                .signWith(Keys.hmacShaKeyFor(secret), SignatureAlgorithm.HS256)
                .compact();
    }
}
