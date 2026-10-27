package fr.siamois.infrastructure.crypto;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.encrypt.Encryptors;
import org.springframework.security.crypto.encrypt.TextEncryptor;
import org.springframework.stereotype.Component;

/**
 * Encrypts the API keys users store in the database (their OpenTheso keys), and decrypts them back.
 * <p>AES-256 in GCM mode (authenticated: a tampered value fails to decrypt), the secret key being derived from
 * {@code siamois.api-key-encryption.password} and {@code .salt} with PBKDF2. A random IV makes every encryption
 * of the same key different. The result is hex-encoded. Changing the password or the salt makes the keys already
 * stored undecryptable: their users have to type them again.</p>
 */
@Component
public class ApiKeyEncryptor {

    private final TextEncryptor encryptor;

    public ApiKeyEncryptor(@Value("${siamois.api-key-encryption.password}") String password,
                           @Value("${siamois.api-key-encryption.salt}") String salt) {
        // Spring Security 6.3's AES-GCM text encryptor (7.x names it AesGcmBytesEncryptor.withPassword)
        this.encryptor = Encryptors.delux(password, salt);
    }

    public String encrypt(String apiKey) {
        return encryptor.encrypt(apiKey);
    }

    /** @throws IllegalStateException when the value was not encrypted with the current password and salt */
    public String decrypt(String encryptedApiKey) {
        return encryptor.decrypt(encryptedApiKey);
    }
}
