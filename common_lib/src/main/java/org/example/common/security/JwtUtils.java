package org.example.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.util.Date;
import java.util.function.Function;

/**
 * Utilitaire JWT partage par tous les microservices.
 * Chaque service doit definir la propriete "app.jwt.secret" (mappee sur la
 * variable d'environnement JWT_SECRET), identique dans tous les services
 * puisqu'elle est utilisee a la fois pour la generation (user_service) et
 * la validation en repli (tous les autres, via JwtAuthenticationFilter).
 */
@Component
public class JwtUtils {

    @Value("${app.jwt.secret}")
    private String secretKeyString;

    private Key signingKey;

    @PostConstruct
    public void init() {
        if (secretKeyString == null || secretKeyString.length() < 32) {
            throw new IllegalArgumentException(
                    "La cle secrete JWT (app.jwt.secret / JWT_SECRET) est null ou trop courte (< 32 caracteres) !");
        }
        this.signingKey = Keys.hmacShaKeyFor(secretKeyString.getBytes());
    }

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        return claimsResolver.apply(extractAllClaims(token));
    }

    public Object getClaimByName(String token, String claimName) {
        return extractAllClaims(token).get(claimName);
    }

    public boolean isTokenValid(String token) {
        try {
            return !extractClaim(token, Claims::getExpiration).before(new Date());
        } catch (Exception e) {
            return false;
        }
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(signingKey)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }
}