package fr.siamois.domain.models.vocabulary;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Objects;

/**
 * An OpenTheso instance, identified by its base URI. Its thesauri are {@link Vocabulary vocabularies}; the API
 * keys of the users are configured once per instance, not per thesaurus.
 */
@Data
@NoArgsConstructor
@Entity
@Table(name = "vocabulary_instance", schema = "public")
public class VocabularyInstance implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "vocabulary_instance_id", nullable = false)
    private Long id;

    @NotNull
    @Column(name = "base_uri", nullable = false, unique = true)
    private String baseUri;

    /**
     * Creates an instance not saved yet.
     */
    public VocabularyInstance(String baseUri) {
        this.baseUri = baseUri;
    }

    /**
     * Two instances are equal when they have the same base URI.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof VocabularyInstance that)) return false;
        return Objects.equals(baseUri, that.baseUri);
    }

    /**
     * Hash of the base URI, consistent with {@link #equals(Object)}.
     */
    @Override
    public int hashCode() {
        return Objects.hashCode(baseUri);
    }
}
