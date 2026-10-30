package fr.siamois.domain.models.vocabulary;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.hibernate.envers.Audited;
import org.hibernate.envers.RelationTargetAuditMode;

import java.io.Serializable;
import java.util.Objects;

@Data
@Entity
@Table(name = "vocabulary", schema = "public", uniqueConstraints = @UniqueConstraint(
        name = "uk_vocabulary_instance_external_id", columnNames = {"fk_vocabulary_instance_id", "external_id"}))
@Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
public class Vocabulary implements Serializable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "vocabulary_id", nullable = false)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "fk_type_id", nullable = false)
    private VocabularyType type;

    @NotNull
    @Column(name = "external_id", nullable = false)
    private String externalVocabularyId;

    @JsonIgnore
    @NotNull
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "fk_vocabulary_instance_id", nullable = false)
    private VocabularyInstance instance;

    /**
     * The base URI of the OpenTheso instance of this vocabulary.
     */
    public String getBaseUri() {
        return instance == null ? null : instance.getBaseUri();
    }

    /**
     * Points this vocabulary to an instance of the given base URI, not saved yet: the instance is resolved
     * against the database when the vocabulary is saved.
     */
    public void setBaseUri(String baseUri) {
        this.instance = baseUri == null ? null : new VocabularyInstance(baseUri);
    }

    /**
     * The complete URI of the thesaurus in its instance.
     */
    @JsonIgnore
    public String getUri() {
        return String.format("%s?idt=%s", getBaseUri(), externalVocabularyId);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Vocabulary that)) return false;

        return Objects.equals(getBaseUri(), that.getBaseUri()) &&
                Objects.equals(externalVocabularyId, that.externalVocabularyId) &&
                Objects.equals(type, that.type);
    }

    @Override
    public int hashCode() {
        return Objects.hash(getBaseUri(), externalVocabularyId, type);
    }

}