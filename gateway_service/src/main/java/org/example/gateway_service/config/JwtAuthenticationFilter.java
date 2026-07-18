package org.example.gateway_service.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.security.Key;

@Component
public class JwtAuthenticationFilter extends AbstractGatewayFilterFactory<JwtAuthenticationFilter.Config> {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    @Value("${jwt.secret}")
    private String secretKey;

    public static class Config {}

    public JwtAuthenticationFilter() {
        super(Config.class);
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            ServerHttpRequest request = exchange.getRequest();

            // 1. Nettoyer les en-têtes potentiellement injectés par le client pour des raisons de sécurité
            ServerHttpRequest cleanedRequest = request.mutate()
                    .headers(httpHeaders -> {
                        httpHeaders.remove("X-User-Email");
                        httpHeaders.remove("X-User-Role");
                        httpHeaders.remove("X-Enterprise-Id");
                        httpHeaders.remove("X-User-Id");
                    })
                    .build();
            ServerWebExchange cleanedExchange = exchange.mutate().request(cleanedRequest).build();

            // 2. Laisser passer les requêtes d'authentification sans token
            String path = request.getURI().getPath();
            log.info("Incoming request to path: {}", path);
            if (path.contains("/api/v1/users/register") ||
                    path.contains("/api/v1/users/login") ||
                    path.contains("/api/v1/users/setup-admin")) {
                log.info("Path {} bypassed security", path);
                return chain.filter(cleanedExchange);
            }

            // 3. Vérifier la présence du header Authorization
            if (!request.getHeaders().containsKey(HttpHeaders.AUTHORIZATION)) {
                log.warn("JWT validation failed for path {}: Missing Authorization header", path);
                return onError(cleanedExchange, "Header d'autorisation manquant", HttpStatus.UNAUTHORIZED);
            }

            String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                log.warn("JWT validation failed for path {}: Invalid Authorization header format (not Bearer)", path);
                return onError(cleanedExchange, "Format de token invalide", HttpStatus.UNAUTHORIZED);
            }

            String token = authHeader.substring(7);

            try {
                // 4. Valider le token et extraire les claims avec la clé secrète partagée
                Key key = Keys.hmacShaKeyFor(secretKey.getBytes(StandardCharsets.UTF_8));
                Claims claims = Jwts.parserBuilder()
                        .setSigningKey(key)
                        .build()
                        .parseClaimsJws(token)
                        .getBody();

                String userId = claims.get("userId", String.class);
                String email = claims.getSubject();
                String role = claims.get("role", String.class);
                Object enterpriseIdObj = claims.get("enterpriseId");
                String enterpriseId = enterpriseIdObj != null ? String.valueOf(enterpriseIdObj) : "";

                log.info("JWT validation succeeded for path {}. User: {}, Role: {}, Enterprise: {}", path, email, role, enterpriseId);

                // 5. Injecter les informations de l'utilisateur dans les headers pour les microservices aval
                ServerHttpRequest modifiedRequest = cleanedRequest.mutate()
                        .header("X-User-Email", email != null ? email : "")
                        .header("X-User-Role", role != null ? role : "")
                        .header("X-User-Id", userId)
                        .header("X-Enterprise-Id", enterpriseId)
                        .build();

                return chain.filter(cleanedExchange.mutate().request(modifiedRequest).build());

            } catch (Exception e) {
                log.error("JWT validation failed for path {}: Exception: {} - {}", path, e.getClass().getSimpleName(), e.getMessage());
                return onError(cleanedExchange, "Token non valide ou expiré", HttpStatus.UNAUTHORIZED);
            }
        };
    }

    private Mono<Void> onError(ServerWebExchange exchange, String err, HttpStatus httpStatus) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(httpStatus);
        return response.setComplete();
    }
}