package fr.siamois.domain.models.vocabulary;

import jakarta.persistence.*;
import lombok.Data;

import java.util.Objects;

@Data
@Entity
@Table(name = "localized_concept_data")
public class LocalizedConceptData {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "label_id")
    private Long id;

    @Column(name = "lang_code", nullable = false)
    private String langCode;

    @Column(name = "label", nullable = false, columnDefinition = "citext")
    private String label;

    @Column(name = "concept_definition", length = Integer.MAX_VALUE)
    private String definition;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fk_concept_id", nullable = false)
    private Concept concept;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fk_field_parent_concept_id")
    private Concept parentConcept;

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof LocalizedConceptData that)) return false;
        return Objects.equals(langCode, that.langCode) && Objects.equals(concept, that.concept);
    }

    @Override
    public int hashCode() {
        return Objects.hash(langCode, concept);
    }
}
