package fr.siamois.domain.models.vocabulary;

import jakarta.persistence.*;
import lombok.Data;
import lombok.Getter;

@Getter
@Entity
@Table(name = "concept_related_link")
public class ConceptRelatedLink {


    @EmbeddedId
    private Id id;

    @MapsId("conceptId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fk_concept_id")
    private Concept concept;

    @MapsId("relatedConceptId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fk_related_concept_id")
    private Concept relatedConcept;

    // The class is more likely to miss fields like current field or dependant field concept.

    public ConceptRelatedLink(Concept concept, Concept relatedConcept) {
        this.id = new Id();
        this.id.setConceptId(concept.getId());
        this.id.setRelatedConceptId(relatedConcept.getId());
        this.concept = concept;
        this.relatedConcept = relatedConcept;
    }

    public ConceptRelatedLink() {
        this.id = new Id();
        this.concept = null;
        this.relatedConcept = null;
    }

    public void setConcept(Concept concept) {
        this.concept = concept;
        this.id.conceptId = concept.getId();
    }

    public void setRelatedConcept(Concept relatedConcept) {
        this.relatedConcept = relatedConcept;
        this.id.relatedConceptId = relatedConcept.getId();
    }

    @Embeddable
    @Data
    public static class Id {
        private Long conceptId;
        private Long relatedConceptId;
    }

}
