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
@Table(name = "vocabulary", schema = "public")
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

    @NotNull
    @Column(name = "base_uri", nullable = false)
    private String baseUri;

    @JsonIgnore
    public String getUri() {
        return String.format("%s?idt=%s", baseUri, externalVocabularyId);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Vocabulary that)) return false;

        return Objects.equals(baseUri, that.baseUri) &&
                Objects.equals(externalVocabularyId, that.externalVocabularyId) &&
                Objects.equals(type, that.type);
    }

    @Override
    public int hashCode() {
        return Objects.hash(baseUri, externalVocabularyId, type);
    }



}