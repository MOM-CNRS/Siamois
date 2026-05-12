package fr.siamois.domain.services.auth;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;

class TokenHasherTest {

    @Test
    void sha256Hex_matchesJavaStandardLibrary() throws NoSuchAlgorithmException {
        String raw = "opaque-refresh-token";
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        String expected = HexFormat.of().formatHex(md.digest(raw.getBytes(StandardCharsets.UTF_8)));

        assertThat(TokenHasher.sha256Hex(raw)).isEqualTo(expected);
    }

    @Test
    void sha256Hex_deterministic() {
        assertThat(TokenHasher.sha256Hex("same")).isEqualTo(TokenHasher.sha256Hex("same"));
    }
}
