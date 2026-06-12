package fr.siamois.ui.config.security.jwt;

import fr.siamois.domain.models.auth.Person;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

@Service
@RequiredArgsConstructor
public class JwtService {

    static final String CLAIM_TYP = "typ";
    static final String TYP_ACCESS = "access";

    private final JwtProperties jwtProperties;
    private SecretKey signingKey;

    @PostConstruct
    void initKey() {
        String raw = jwtProperties.getSecret();
        if (raw == null || raw.isBlank()) {
            throw new IllegalStateException(
                    "JWT secret is missing or blank. Set siamois.jwt.secret or environment variable SIAMOIS_JWT_SECRET "
                            + "(HS256 requires at least 32 UTF-8 characters).");
        }
        String secret = raw.trim();
        if (secret.length() < 32) {
            throw new IllegalStateException(
                    "JWT secret must be at least 32 characters (256 bits) for HS256; got length " + secret.length() + ".");
        }
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        this.signingKey = Keys.hmacShaKeyFor(bytes);
    }

    public String createAccessToken(Person person) {
        Instant now = Instant.now();
        Instant exp = now.plus(jwtProperties.getAccessTokenValidity());
        return Jwts.builder()
                .subject(String.valueOf(person.getId()))
                .issuedAt(Date.from(now))
                .expiration(Date.from(exp))
                .claim(CLAIM_TYP, TYP_ACCESS)
                .signWith(signingKey)
                .compact();
    }

    public long accessTokenExpiresInSeconds() {
        return Math.max(1L, jwtProperties.getAccessTokenValidity().getSeconds());
    }

    /**
     * @throws JwtException si signature ou format invalide
     * @throws ExpiredJwtException si expiré
     */
    public Claims parseAndValidateAccessToken(String token) throws JwtException {
        Claims claims = Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        if (!TYP_ACCESS.equals(claims.get(CLAIM_TYP))) {
            throw new JwtException("Not an access token");
        }
        return claims;
    }
}
