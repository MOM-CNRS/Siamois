package fr.siamois.domain.models.vocabulary;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.envers.Audited;
import org.hibernate.envers.NotAudited;

import java.io.Serializable;
import java.util.Objects;
import java.util.Set;

@Data
@Entity
@Table(name = "concept")
@NoArgsConstructor
@Audited
public class Concept implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "concept_id", nullable = false)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "fk_vocabulary_id", nullable = false)
    private Vocabulary vocabulary;

    @Column(name = "external_id", length = Integer.MAX_VALUE)
    private String externalId;

    @Column(name = "is_deleted", nullable = false, columnDefinition = "boolean default false")
    private boolean isDeleted = false;

    @NotAudited
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "concept_related",
            joinColumns = {@JoinColumn(name = "fk_concept_id")},
            inverseJoinColumns = {@JoinColumn(name = "fk_related_concept_id")},
            indexes = {@Index(name = "idx_related_concepts", columnList = "fk_concept_id")}
    )
    private Set<Concept> relatedConcepts;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Concept concept)) return false;

        return Objects.equals(externalId, concept.externalId) &&
                Objects.equals(vocabulary, concept.vocabulary);
    }

    @Override
    public int hashCode() {
        return Objects.hash(externalId, vocabulary);
    }

    public static class Builder {
        private Long id;
        private Vocabulary vocabulary;
        private String externalId;

        public Builder id(Long id) {
            this.id = id;
            return this;
        }

        public Builder vocabulary(Vocabulary vocabulary) {
            this.vocabulary = vocabulary;
            return this;
        }

        public Builder externalId(String externalId) {
            this.externalId = externalId;
            return this;
        }

        public Concept build() {
            Concept concept = new Concept();
            concept.setId(this.id);
            concept.setVocabulary(this.vocabulary);
            concept.setExternalId(this.externalId);
            return concept;
        }
    }


}