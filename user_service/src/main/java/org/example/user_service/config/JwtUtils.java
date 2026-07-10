package org.example.user_service.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.example.user_service.entity.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;


import java.security.Key;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

@Component
public class JwtUtils {

    @Value("${app.jwt.secret}")
    private String secretKeyString; // Injecté en premier par Spring

    private Key signingKey; // Sera initialisé juste après

    @PostConstruct
    public void init() {
        // Cette méthode s'exécute APRÈS l'injection de secretKeyString
        if (secretKeyString == null || secretKeyString.length() < 32) {
            throw new IllegalArgumentException("La clé secrète JWT est null ou trop courte !");
        }
        this.signingKey = Keys.hmacShaKeyFor(secretKeyString.getBytes());
    }
    // Durée de validité du Token : 24 heures (en millisecondes)
    @Value("${app.jwt.expiration}")
    private long jwtExpiration ;// 24 heures

    /**
     * 1. Extrait l'email (Subject) du Token
     */
    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    /**
     * 2. Extrait une information spécifique (Claim) du Token
     */
    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    /**
     * 3. Extrait un objet personnalisé depuis les claims (ex: Long, String) par son nom
     */
    public Object getClaimByName(String token, String claimName) {
        final Claims claims = extractAllClaims(token);
        return claims.get(claimName);
    }

    /**
     * 4. Génère un Token JWT pour un utilisateur avec ses rôles et son entreprise (Multi-tenant)
     */
    public String generateToken(User user) {
        Map<String, Object> extraClaims = new HashMap<>();
        extraClaims.put("enterpriseId", user.getEnterpriseId()); // Isolation des données
        extraClaims.put("role", user.getRole().name());         // Rôle pour Spring Security & Angular

        return Jwts.builder()
                .setClaims(extraClaims)
                .setSubject(user.getEmail())
                .setIssuedAt(new Date(System.currentTimeMillis()))
                .setExpiration(new Date(System.currentTimeMillis() + jwtExpiration))
                .signWith(signingKey, SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * 5. Valide la structure mathématique et l'expiration du Token
     */
    public boolean isTokenValid(String token) {
        try {
            return !isTokenExpired(token);
        } catch (Exception e) {
            // Si la signature est corrompue ou le token est altéré, l'exception est interceptée et retourne false
            return false;
        }
    }

    /**
     * Vérifie si le token a dépassé sa date d'expiration
     */
    private boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    /**
     * Extrait la date d'expiration du token
     */
    private Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    /**
     * Parse le token en utilisant la clé de signature pour en extraire tous les Claims
     */
    private Claims extractAllClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(signingKey)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }
}