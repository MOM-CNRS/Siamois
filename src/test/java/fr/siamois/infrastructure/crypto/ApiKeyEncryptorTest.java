package fr.siamois.infrastructure.crypto;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApiKeyEncryptorTest {

    private static final String SALT = "5c0744940b5c369b";

    private final ApiKeyEncryptor encryptor = new ApiKeyEncryptor("password", SALT);

    @Test
    void decrypt_givesBackTheEncryptedKey() {
        String encrypted = encryptor.encrypt("my-opentheso-key");

        assertThat(encrypted).isNotEqualTo("my-opentheso-key").matches("[0-9a-f]+");
        assertThat(encryptor.decrypt(encrypted)).isEqualTo("my-opentheso-key");
    }

    @Test
    void encrypt_neverGivesTheSameValueTwice() {
        assertThat(encryptor.encrypt("my-opentheso-key")).isNotEqualTo(encryptor.encrypt("my-opentheso-key"));
    }

    @Test
    void decrypt_withAnotherPassword_fails() {
        String encrypted = encryptor.encrypt("my-opentheso-key");

        ApiKeyEncryptor newEncryptor = new ApiKeyEncryptor("other", SALT);
        assertThatThrownBy(() -> newEncryptor.decrypt(encrypted))
                .isInstanceOf(IllegalStateException.class);
    }
}
