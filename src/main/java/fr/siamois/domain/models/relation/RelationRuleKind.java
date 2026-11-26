package fr.siamois.domain.models.relation;

import fr.siamois.domain.models.vocabulary.Concept;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A kind of record that one side of a relation accepts under a rule set, optionally narrowed to a subtype (a
 * recording unit of type US). A side with no row accepts any record, like an untyped pointer in Heurist.
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "relation_rule_kind")
public class RelationRuleKind {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "relation_rule_kind_id", nullable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fk_relation_rule_id", nullable = false)
    private RelationRule rule;

    @Enumerated(EnumType.STRING)
    @Column(name = "side", nullable = false, length = 8)
    private RelationSide side;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 32)
    private RelationEndKind kind;

    /** The type concept the record must have, or null for any record of that kind. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fk_subtype_concept_id")
    private Concept subtype;
}
