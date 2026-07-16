package org.example.user_service.config;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
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

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtils jwtUtils; // on la remet : nécessaire pour le fallback

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        if (SecurityContextHolder.getContext().getAuthentication() == null) {

            // ── CHEMIN 1 : headers de confiance injectés par la gateway ──
            final String headerEmail = request.getHeader("X-User-Email");
            final String headerRole = request.getHeader("X-User-Role");
            final String headerEnterpriseId = request.getHeader("X-Enterprise-Id");

            if (headerEmail != null && !headerEmail.isEmpty() && headerRole != null && !headerRole.isEmpty()) {
                authenticate(headerEmail, headerRole, headerEnterpriseId, request);
            } else {
                // ── CHEMIN 2 : pas de headers gateway -> valider le JWT nous-mêmes ──
                // (appel Feign direct entre services, ou appel de test hors gateway)
                final String authHeader = request.getHeader("Authorization");
                if (authHeader != null && authHeader.startsWith("Bearer ")) {
                    String jwt = authHeader.substring(7);
                    try {
                        if (jwtUtils.isTokenValid(jwt)) {
                            String email = jwtUtils.extractUsername(jwt);
                            String role = (String) jwtUtils.getClaimByName(jwt, "role");
                            Object entIdClaim = jwtUtils.getClaimByName(jwt, "enterpriseId");
                            String enterpriseId = entIdClaim != null ? entIdClaim.toString() : null;
                            authenticate(email, role, enterpriseId, request);
                        } else {
                            System.out.println("=== JWT REJETÉ (fallback) : token invalide/expiré ===");
                        }
                    } catch (Exception e) {
                        System.out.println("=== JWT EXTRACTION FAILED (fallback) : " + e.getClass().getSimpleName() + " - " + e.getMessage());
                    }
                }
            }
        }

        filterChain.doFilter(request, response);
    }

    private void authenticate(String email, String role, String enterpriseIdStr, HttpServletRequest request) {
        List<SimpleGrantedAuthority> authorities = Collections.singletonList(new SimpleGrantedAuthority(role));

        UsernamePasswordAuthenticationToken authToken =
                new UsernamePasswordAuthenticationToken(email, null, authorities);
        authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authToken);

        if (enterpriseIdStr != null && !enterpriseIdStr.isEmpty()) {
            try {
                request.setAttribute("enterpriseId", Long.parseLong(enterpriseIdStr));
            } catch (NumberFormatException ignored) {
                // enterpriseId absent ou non numérique (ex. super-admin) — on ignore volontairement
            }
        }
    }
}