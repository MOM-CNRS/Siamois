package fr.siamois.domain.models.vocabulary;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Entity
@Setter
@Table(name = "concept_hierarchy")
public class ConceptHierarchy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "concept_hierarchy_id", nullable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fk_parent_concept_id")
    private Concept parent;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fk_child_concept_id")
    private Concept child;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fk_parent_field_context_id")
    private Concept parentFieldContext;

    public ConceptHierarchy(Concept parent, Concept child, Concept parentFieldContext) {
        this.parent = parent;
        this.child = child;
        this.parentFieldContext = parentFieldContext;
    }

    public ConceptHierarchy() {
        parent = null;
        child = null;
        parentFieldContext = null;
    }

}
