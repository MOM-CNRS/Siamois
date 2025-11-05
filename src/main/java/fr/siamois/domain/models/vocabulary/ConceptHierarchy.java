package fr.siamois.domain.models.vocabulary;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Check;
import org.hibernate.annotations.Checks;

@Getter
@Entity
@Setter
@Table(name = "concept_hierarchy")
@Checks({
        @Check(name = "ck_different_concepts_concept_hierarchy", constraints = "fk_parent_concept_id <> fk_child_concept_id"),
        @Check(name = "ck_parent_is_not_field_concept_hierarchy", constraints = "fk_parent_concept_id <> fk_parent_field_context_id")
})
public class ConceptHierarchy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "concept_hierarchy_id", nullable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "fk_parent_concept_id")
    private Concept parent;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
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
