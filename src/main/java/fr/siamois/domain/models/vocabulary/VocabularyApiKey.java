package fr.siamois.domain.models.vocabulary;

import fr.siamois.domain.models.auth.Person;
import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "vocabulary_api_key")
public class VocabularyApiKey {

    @EmbeddedId
    private VocabularyApiKeyId id;

    @MapsId("vocabularyId")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fk_vocabulary_id")
    private Vocabulary vocabulary;

    @MapsId("personId")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fk_person_id")
    private Person person;

    /** Hex-encoded AES-GCM: about twice the key's length plus 64 characters. */
    @Column(name = "encrypted_key", nullable = false, length = 1024)
    private String encryptedKey;

    public VocabularyApiKey() {
        this.id = new VocabularyApiKeyId();
    }

    @Data
    @Embeddable
    public static class VocabularyApiKeyId {
        private Long vocabularyId;
        private Long personId;
    }

}
