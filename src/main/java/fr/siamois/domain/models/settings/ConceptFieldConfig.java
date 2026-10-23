package fr.siamois.domain.models.settings;

import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.models.vocabulary.Vocabulary;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Objects;

/**
 * Where a field code lives in a thesaurus: the concept that carries that code (its notation) in the
 * thesaurus's fields branch, whose subtree is the list of values. One row per (thesaurus, code),
 * shared by everyone who reads that thesaurus.
 */
@Entity
@Table(name = "concept_field_config",
        uniqueConstraints = @UniqueConstraint(name = "uk_concept_field_config_vocabulary_code",
                columnNames = {"fk_vocabulary_id", "field_code"}))
@Data
public class ConceptFieldConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "config_id", nullable = false)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fk_vocabulary_id", nullable = false)
    private Vocabulary vocabulary;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fk_concept_id", nullable = false)
    private Concept concept;

    @NotNull
    @Column(name = "field_code", nullable = false, length = Integer.MAX_VALUE)
    private String fieldCode;

    @Column(name = "existing_hash", length = Integer.MAX_VALUE)
    private String existingHash = null;

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof ConceptFieldConfig that)) return false;
        return Objects.equals(vocabulary, that.vocabulary)
                && Objects.equals(fieldCode, that.fieldCode);
    }

    @Override
    public int hashCode() {
        return Objects.hash(vocabulary, fieldCode);
    }
}
