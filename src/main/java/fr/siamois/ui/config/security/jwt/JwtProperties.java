package fr.siamois.ui.config.security.jwt;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@Data
@ConfigurationProperties(prefix = "siamois.jwt")
public class JwtProperties {

    /**
     * Secret UTF-8 pour signature HS256 ({@link io.jsonwebtoken.security.Keys#hmacShaKeyFor}).
     */
    private String secret;

    private Duration accessTokenValidity = Duration.ofMinutes(15);

    private Duration refreshTokenValidity = Duration.ofDays(30);
}
