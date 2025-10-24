package fr.siamois.domain.services.vocabulary;

import fr.siamois.domain.models.vocabulary.Concept;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "concept_hierarchy")
public class ConceptHierarchy {

    @EmbeddedId
    private ConceptRelationId id;

    @MapsId("parentId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fk_parent_concept_id")
    private Concept parent;

    @MapsId("childId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fk_child_concept_id")
    private Concept child;

    public ConceptHierarchy(Concept parent, Concept child) {
        id = new ConceptRelationId(parent.getId(), child.getId());
        this.parent = parent;
        this.child = child;
    }

    public ConceptHierarchy() {
        id = new ConceptRelationId();
        parent = null;
        child = null;
    }

    public void setChild(Concept child) {
        this.child = child;
        id.childId = child.getId();
    }

    public void setParent(Concept parent) {
        this.parent = parent;
        id.parentId = parent.getId();
    }

    @AllArgsConstructor
    @NoArgsConstructor
    @Embeddable
    @EqualsAndHashCode
    public static class ConceptRelationId {
        public long parentId;
        public long childId;
    }

}
