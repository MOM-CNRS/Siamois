package fr.siamois.domain.models.vocabulary;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.envers.Audited;

import java.io.Serializable;
import java.util.Objects;

@Data
@Entity
@Table(name = "concept")
@NoArgsConstructor
@Audited
public class Concept implements Serializable {

    public Concept(Concept concept) {
        this.id = concept.getId();
        this.vocabulary = concept.getVocabulary();
        this.externalId = concept.getExternalId();
    }

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