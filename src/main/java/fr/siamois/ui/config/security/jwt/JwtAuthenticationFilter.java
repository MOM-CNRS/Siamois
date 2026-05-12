package fr.siamois.ui.config.security.jwt;

import fr.siamois.domain.models.auth.Person;
import fr.siamois.infrastructure.database.repositories.person.PersonRepository;
import fr.siamois.ui.config.security.ApiUnauthorizedJsonWriter;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

/**
 * Valide le JWT Bearer sur les routes {@code /api/v1/**} (chaîne dédiée), avec principal {@link Person}
 * pour {@link fr.siamois.utils.AuthenticatedUserUtils}.
 * Enregistré uniquement dans cette chaîne (pas comme filtre Servlet global — voir {@code FilterRegistrationBean} désactivé).
 */
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String AUTHORIZATION = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    /**
     * Hors JWT : uniquement obtention / renouvellement des jetons (sans Bearer).
     * Tout le reste de {@code /api/v1/**} exige un en-tête {@code Authorization: Bearer} avec un access token valide.
     */
    private static final Set<String> PUBLIC_AUTH_PATHS = Set.of(
            "/api/v1/auth/login",
            "/api/v1/auth/refresh"
    );

    private final JwtService jwtService;
    private final PersonRepository personRepository;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        String path = normalizePath(request.getServletPath());
        if (PUBLIC_AUTH_PATHS.contains(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        String header = request.getHeader(AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            writeUnauthorized(response, "Missing or invalid Authorization header");
            return;
        }

        String token = header.substring(BEARER_PREFIX.length()).trim();
        if (token.isEmpty()) {
            writeUnauthorized(response, "Missing or invalid Authorization header");
            return;
        }

        try {
            var claims = jwtService.parseAndValidateAccessToken(token);
            long personId = Long.parseLong(claims.getSubject());
            Person person = personRepository.findById(personId).orElse(null);
            if (person == null || !person.isEnabled()) {
                writeUnauthorized(response, "Unknown or disabled user");
                return;
            }
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(person, null, person.getAuthorities());
            SecurityContextHolder.getContext().setAuthentication(authentication);
            filterChain.doFilter(request, response);
        } catch (JwtException | IllegalArgumentException e) {
            writeUnauthorized(response, "Invalid or expired token");
        }
    }

    private static void writeUnauthorized(HttpServletResponse response, String message) throws IOException {
        ApiUnauthorizedJsonWriter.write(response, message);
    }

    /** Uniformise les chemins avec ou sans slash final (ex. {@code /api/v1/auth/login/}). */
    private static String normalizePath(String servletPath) {
        if (servletPath == null || servletPath.length() <= 1) {
            return servletPath == null ? "" : servletPath;
        }
        return servletPath.endsWith("/") ? servletPath.substring(0, servletPath.length() - 1) : servletPath;
    }
}
