package fr.siamois.domain.models.relation;

import fr.siamois.domain.models.vocabulary.Concept;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A qualifier concept and its mirror under a rule set ("recouvre" / "est recouverte par"). Only the qualifier is
 * stored on a link and the mirror is derived from it, so the two never diverge; a concept with no pair is its own
 * mirror. A pair is one to one in a rule set, so it also turns a mirror picked from the other record back into the
 * qualifier to store.
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "relation_rule_mirror_concept")
public class RelationRuleMirrorConcept {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "relation_rule_mirror_concept_id", nullable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fk_relation_rule_id", nullable = false)
    private RelationRule rule;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fk_concept_id", nullable = false)
    private Concept concept;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fk_mirror_concept_id", nullable = false)
    private Concept mirrorConcept;
}
