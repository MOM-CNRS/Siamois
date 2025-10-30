package fr.siamois.domain.models.vocabulary.label;

import fr.siamois.domain.models.vocabulary.Concept;
import jakarta.persistence.*;
import lombok.*;

import java.util.Objects;

@Getter
@Setter
@MappedSuperclass
public abstract class ConceptLabel {

    @EmbeddedId
    protected Id id;

    protected ConceptLabel() {
        this.id = new Id();
    }

    @MapsId("conceptId")
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "fk_concept_id", nullable = false)
    protected Concept concept;

    @Column(name = "label", nullable = false, columnDefinition = "citext")
    protected String label;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fk_field_parent_concept_id")
    protected Concept parentConcept;

    public abstract LabelType getLabelType();

    public void setLangCode(String langCode) {
        id.langCode = langCode;
    }

    public String getLangCode() {
        return id.langCode;
    }

    @Embeddable
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Id {
        protected Long conceptId;
        protected String langCode;

        public Id(Concept concept, String langCode) {
            this.conceptId = concept.getId();
            this.langCode = langCode;
        }

    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof ConceptLabel that)) return false;
        return Objects.equals(concept, that.concept) && Objects.equals(label, that.label);
    }

    @Override
    public int hashCode() {
        return Objects.hash(concept, label);
    }
}
