package org.example.user_service.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.Data;
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

     private final JwtUtils jwtUtils;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        // 1. Extraire le header "Authorization"
        final String authHeader = request.getHeader("Authorization");
        final String jwt;
        final String userEmail;

        // 2. Vérifier si le header est présent et commence bien par "Bearer "
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response); // On passe au filtre suivant (Spring bloquera si la route est protégée)
            return;
        }

        // 3. Extraire le token (on retire les 7 caractères de "Bearer ")
        jwt = authHeader.substring(7);


         userEmail = jwtUtils.extractUsername(jwt);
       // userEmail = "mock-email@domain.com"; // Simulation temporaire pour la compilation

        // 4. Si l'email est extrait et que l'utilisateur n'est pas encore authentifié dans le contexte Spring
        if (userEmail != null && SecurityContextHolder.getContext().getAuthentication() == null) {


             boolean isTokenValid = jwtUtils.isTokenValid(jwt);
            //boolean isTokenValid = true; // Simulation temporaire

            if (isTokenValid) {

                String role = (String) jwtUtils.getClaimByName(jwt, "role");
                Long enterpriseId = ((Number) jwtUtils.getClaimByName(jwt, "enterpriseId")).longValue();

               // String mockRole = "ROLE_ADMIN";
                List<SimpleGrantedAuthority> authorities = Collections.singletonList(new SimpleGrantedAuthority(role));

                // 5. Créer l'objet d'authentification requis par Spring Security
                UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                        userEmail,
                        null, // Le mot de passe n'est pas nécessaire ici, le token fait foi
                        authorities
                );

                // On peut attacher des détails supplémentaires de la requête HTTP
                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                // 6. Injection CRITIQUE : On stocke l'utilisateur authentifié dans le contexte global de Spring
                SecurityContextHolder.getContext().setAuthentication(authToken);

                // Optionnel/Avancé DevSecOps : Stocker l'enterpriseId dans un attribut de la requête
                // pour que tes contrôleurs puissent y accéder facilement
                 request.setAttribute("enterpriseId", enterpriseId);
            }
        }

        // 7. Continuer la chaîne des filtres
        filterChain.doFilter(request, response);
    }
}