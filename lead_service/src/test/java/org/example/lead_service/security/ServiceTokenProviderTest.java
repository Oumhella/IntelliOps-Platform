package org.example.lead_service.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.example.common.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class ServiceTokenProviderTest {

    private static final String SECRET = "a-test-secret-that-is-at-least-32-characters-long";

    @AfterEach
    void clearTenantContext() {
        TenantContext.clear();
    }

    @Test
    void createsShortLivedServiceIdentityWhileKeepingTenantAndActor() {
        TenantContext.setEnterpriseId(7L);
        TenantContext.setUserId(42L);

        String token = new ServiceTokenProvider(SECRET, 120).forCurrentRequest();
        Claims claims = Jwts.parserBuilder()
                .setSigningKey(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .build()
                .parseClaimsJws(token)
                .getBody();

        assertThat(claims.getSubject()).isEqualTo("lead-service");
        assertThat(claims.get("role")).isEqualTo("ROLE_INTEGRATION_SERVICE");
        assertThat(claims.get("enterpriseId", Number.class).longValue()).isEqualTo(7L);
        assertThat(claims.get("userId", Number.class).longValue()).isEqualTo(42L);
        assertThat(claims.getExpiration().getTime() - claims.getIssuedAt().getTime()).isEqualTo(120_000L);
    }
}
