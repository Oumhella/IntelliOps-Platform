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
 * Shared authentication boundary for every HTTP microservice.
 *
 * Identity is always derived from the signed bearer token. Forwarded identity
 * headers are deliberately ignored: containers can be reached by other
 * containers on the Docker network, so a header alone is never proof of who
 * the caller is. Feign propagates the original Authorization header for
 * service-to-service calls.
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
            String authHeader = request.getHeader("Authorization");
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                String jwt = authHeader.substring(7);
                try {
                    if (jwtUtils.isTokenValid(jwt)) {
                        String email = jwtUtils.extractUsername(jwt);
                        String role = (String) jwtUtils.getClaimByName(jwt, "role");
                        Object entIdClaim = jwtUtils.getClaimByName(jwt, "enterpriseId");
                        Object userIdClaim = jwtUtils.getClaimByName(jwt, "userId");

                        if (email != null && !email.isBlank()
                                && role != null && !role.isBlank()
                                && userIdClaim != null) {
                            authenticate(
                                    email,
                                    role,
                                    entIdClaim != null ? entIdClaim.toString() : null,
                                    userIdClaim.toString(),
                                    request
                            );
                        } else {
                            logger.warn("JWT rejete : claims d'identite requis absents");
                        }
                    } else {
                        logger.warn("JWT rejete : token invalide ou expire");
                    }
                } catch (Exception e) {
                    logger.warn("Echec extraction JWT : " + e.getClass().getSimpleName() + " - " + e.getMessage());
                }
            }
        }

        try {
            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }

    private void authenticate(String email, String role, String enterpriseIdStr, String userIdStr, HttpServletRequest request) {
        List<SimpleGrantedAuthority> authorities = Collections.singletonList(new SimpleGrantedAuthority(role));
        UsernamePasswordAuthenticationToken authToken =
                new UsernamePasswordAuthenticationToken(email, null, authorities);
        authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authToken);

        if (enterpriseIdStr != null && !enterpriseIdStr.isEmpty()) {
            try {
                Long entId = Long.parseLong(enterpriseIdStr);
                request.setAttribute("enterpriseId", entId);
                TenantContext.setEnterpriseId(entId);
            } catch (NumberFormatException ignored) {
                // enterpriseId absent ou non numerique (ex. super-admin futur) -- ignore volontairement
            }
        }
        if (userIdStr != null && !userIdStr.isEmpty()) {
            try {
                Long uId = Long.parseLong(userIdStr);
                request.setAttribute("userId", uId);
                TenantContext.setUserId(uId);
            }
            catch (NumberFormatException ignored) {}
        }
    }
}
