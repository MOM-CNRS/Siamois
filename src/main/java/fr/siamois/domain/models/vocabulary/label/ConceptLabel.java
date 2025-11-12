package fr.siamois.domain.models.vocabulary.label;

import fr.siamois.domain.models.vocabulary.Concept;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.Objects;

/**
 * Abstract base class for different types of concept labels.
 * It uses a composite primary key consisting of concept ID and language code.
 *
 * @author Julien Linget
 */
@Getter
@Setter
@Entity
@Table(name = "concept_label", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"fk_concept_id", "lang_code", "label_type"})
})
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "label_type", discriminatorType = DiscriminatorType.INTEGER)
public abstract class ConceptLabel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "concept_label_id")
    protected Long id;

    /**
     * The concept associated with this label.
     */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "fk_concept_id", nullable = false)
    protected Concept concept;

    @Column(name = "label", nullable = false, columnDefinition = "citext")
    protected String label;

    @Column(name = "lang_code", nullable = false, length = 3)
    protected String langCode;

    /**
     *  This field is used as caching for faster search when autocompleting labels.
     *  This parent concept is a concept that is associated with the field in the application.
     *  In this field, the value can be the current concept associated with the label.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fk_field_parent_concept_id")
    protected Concept parentConcept;

    public abstract LabelType getLabelType();

    public boolean isAltLabel() {
        return getLabelType() == LabelType.ALT_LABEL;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof ConceptLabel that)) return false;
        return Objects.equals(concept, that.concept)
                && Objects.equals(label, that.label)
                && Objects.equals(langCode, that.langCode);
    }

    @Override
    public int hashCode() {
        return Objects.hash(concept, label, langCode);
    }
}
