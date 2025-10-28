package fr.siamois.domain.services.vocabulary;

import fr.siamois.domain.models.vocabulary.Concept;
import jakarta.persistence.*;
import lombok.*;

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

    @MapsId("parentFieldContextId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fk_parent_field_context_id")
    private Concept parentFieldContext;

    public ConceptHierarchy(Concept parent, Concept child, Concept parentFieldContext) {
        id = new ConceptRelationId(parent.getId(), child.getId(), parentFieldContext.getId());
        this.parent = parent;
        this.child = child;
        this.parentFieldContext = parentFieldContext;
    }

    public ConceptHierarchy() {
        id = new ConceptRelationId();
        parent = null;
        child = null;
        parentFieldContext = null;
    }

    public void setChild(Concept child) {
        this.child = child;
        id.childId = child.getId();
    }

    public void setParent(Concept parent) {
        this.parent = parent;
        id.parentId = parent.getId();
    }

    public void setParentFieldContext(Concept parentFieldContext) {
        this.parentFieldContext = parentFieldContext;
        id.parentFieldContextId = parentFieldContext.getId();
    }

    @AllArgsConstructor
    @NoArgsConstructor
    @Embeddable
    @EqualsAndHashCode
    @Getter
    @Setter
    public static class ConceptRelationId {
        private long parentId;
        private long childId;
        private long parentFieldContextId;
    }

}
