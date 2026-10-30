package fr.siamois.domain.models.vocabulary;

import fr.siamois.domain.models.auth.Person;
import jakarta.persistence.*;
import lombok.Data;

/**
 * The OpenTheso API key of a person for a whole instance: it serves every thesaurus of that instance.
 */
@Data
@Entity
@Table(name = "vocabulary_api_key")
public class VocabularyApiKey {

    @EmbeddedId
    private VocabularyApiKeyId id;

    @MapsId("instanceId")
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "fk_vocabulary_instance_id")
    private VocabularyInstance instance;

    @MapsId("personId")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fk_person_id")
    private Person person;

    /** Hex-encoded AES-GCM: about twice the key's length plus 64 characters. */
    @Column(name = "encrypted_key", nullable = false, length = Integer.MAX_VALUE)
    private String encryptedKey;

    public VocabularyApiKey() {
        this.id = new VocabularyApiKeyId();
    }

    @Data
    @Embeddable
    public static class VocabularyApiKeyId {
        private Long instanceId;
        private Long personId;
    }

}
