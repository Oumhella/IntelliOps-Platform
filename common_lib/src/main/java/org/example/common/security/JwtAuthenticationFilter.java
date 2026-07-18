package org.example.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

/**
 * Filtre d'authentification hybride, partage par tous les microservices.
 *
 * Chemin 1 (prioritaire) : fait confiance aux headers X-User-Email / X-User-Role /
 * X-Enterprise-Id injectes par gateway_service APRES validation du JWT.
 * Ce chemin suppose que gateway_service supprime systematiquement ces headers
 * s'ils sont presents dans une requete entrante (deja fait cote gateway).
 *
 * Chemin 2 (repli) : si ces headers sont absents (appel Feign direct
 * service-a-service via Eureka, contournant la gateway), le filtre revalide
 * le JWT brut lui-meme via JwtUtils.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtils jwtUtils;

    public JwtAuthenticationFilter(JwtUtils jwtUtils) {
        this.jwtUtils = jwtUtils;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        if (SecurityContextHolder.getContext().getAuthentication() == null) {

            String headerEmail = request.getHeader("X-User-Email");
            String headerRole = request.getHeader("X-User-Role");
            String headerEnterpriseId = request.getHeader("X-Enterprise-Id");
            String headerUserId = request.getHeader("X-User-Id");

            if (headerEmail != null && !headerEmail.isEmpty()
                    && headerRole != null && !headerRole.isEmpty()
                    && headerUserId != null && !headerUserId.isEmpty()) {
                authenticate(headerEmail, headerRole, headerEnterpriseId, headerUserId , request);
            } else {
            String authHeader = request.getHeader("Authorization");
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                String jwt = authHeader.substring(7);
                try {
                    if (jwtUtils.isTokenValid(jwt)) {
                        String email = jwtUtils.extractUsername(jwt);
                        String role = (String) jwtUtils.getClaimByName(jwt, "role");
                        Object entIdClaim = jwtUtils.getClaimByName(jwt, "enterpriseId");
                        Object userIdClaim = jwtUtils.getClaimByName(jwt, "userId");

                        authenticate(
                                email,
                                role,
                                entIdClaim != null ? entIdClaim.toString() : null,
                                userIdClaim != null ? userIdClaim.toString() : null,
                                request
                        );
                    } else {
                        logger.warn("JWT rejete (fallback) : token invalide ou expire");
                    }
                } catch (Exception e) {
                    logger.warn("Echec extraction JWT (fallback) : " + e.getClass().getSimpleName() + " - " + e.getMessage());
                }
            }
        }
        }

        filterChain.doFilter(request, response);
    }

    private void authenticate(String email, String role, String enterpriseIdStr, String userIdStr, HttpServletRequest request) {
        List<SimpleGrantedAuthority> authorities = Collections.singletonList(new SimpleGrantedAuthority(role));
        UsernamePasswordAuthenticationToken authToken =
                new UsernamePasswordAuthenticationToken(email, null, authorities);
        authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authToken);

        if (enterpriseIdStr != null && !enterpriseIdStr.isEmpty()) {
            try {
                request.setAttribute("enterpriseId", Long.parseLong(enterpriseIdStr));
            } catch (NumberFormatException ignored) {
                // enterpriseId absent ou non numerique (ex. super-admin futur) -- ignore volontairement
            }
        }
        if (userIdStr != null && !userIdStr.isEmpty()) {
            try { request.setAttribute("userId", Long.parseLong(userIdStr)); }
            catch (NumberFormatException ignored) {}
        }
    }
}