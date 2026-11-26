package fr.siamois.domain.models.relation;

import fr.siamois.domain.models.form.customfield.CustomField;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * The identity of a relation, global (docs/modele-relations.md, §3.2): its two fields, one per side (the same field
 * when the relation is symmetric), and what fixes the meaning of its links. Its links point to it, not to a field:
 * each field reads them from its own side. None of this changes once the definition has links. What only controls
 * input (accepted kinds, cycles, single choice, exclusions, mirror pairs) is in {@link RelationRule}, per scope.
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "relation_definition")
public class RelationDefinition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "relation_definition_id", nullable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fk_source_field_id", nullable = false)
    private CustomField sourceField;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fk_target_field_id", nullable = false)
    private CustomField targetField;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 16)
    private RelationKind kind = RelationKind.SIMPLE;

    /** The relation is its own mirror: both sides are the same field, and a pair is stored in one order only. */
    @Column(name = "is_symmetric", nullable = false)
    private boolean symmetric;

    /** The relation arranges records in a tree: forces "no cycle" in all its rules. */
    @Column(name = "is_hierarchical", nullable = false)
    private boolean hierarchical;

    /** For a complex relation, the concept field whose value qualifies a link ("recouvre"), or null for none. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fk_qualifier_field_id")
    private CustomField qualifierField;

    public boolean isSimple() {
        return kind == RelationKind.SIMPLE;
    }
}
