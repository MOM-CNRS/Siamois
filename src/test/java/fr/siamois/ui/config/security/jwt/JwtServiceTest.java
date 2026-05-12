package fr.siamois.ui.config.security.jwt;

import fr.siamois.domain.models.auth.Person;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        JwtProperties props = new JwtProperties();
        props.setSecret("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef");
        jwtService = new JwtService(props);
        jwtService.initKey();
    }

    @Test
    void createAndParseAccessToken_roundTrip() {
        Person p = new Person();
        p.setId(42L);

        String jwt = jwtService.createAccessToken(p);
        assertThat(jwt).isNotBlank();

        var claims = jwtService.parseAndValidateAccessToken(jwt);
        assertThat(claims.getSubject()).isEqualTo("42");
        assertThat(claims.get(JwtService.CLAIM_TYP)).isEqualTo(JwtService.TYP_ACCESS);
    }

    @Test
    void parseAccessToken_rejectsGarbage() {
        assertThatThrownBy(() -> jwtService.parseAndValidateAccessToken("not.a.jwt"))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void parseAccessToken_rejectsWrongTypClaim() {
        String secret = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
        SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        String wrongTypToken = Jwts.builder()
                .subject("1")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .claim(JwtService.CLAIM_TYP, "refresh")
                .signWith(key)
                .compact();

        assertThatThrownBy(() -> jwtService.parseAndValidateAccessToken(wrongTypToken))
                .isInstanceOf(JwtException.class)
                .hasMessageContaining("Not an access token");
    }
}
