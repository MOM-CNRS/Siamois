package fr.siamois.domain.models.relation;

import fr.siamois.domain.models.vocabulary.Concept;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.envers.Audited;
import org.hibernate.envers.RelationTargetAuditMode;

/**
 * A link between two records, stored once (docs/modele-relations.md, §3.5). Each end is a record kind and its id,
 * with no foreign key: the writing service checks the record exists, and a trigger on each business table removes
 * the links of a deleted record (those deletions leave no revision).
 * <p>
 * {@link #simple} copies the definition's kind: a partial unique index on the simple links keeps them free of
 * duplicates in the database. A symmetric pair is ordered before it is stored, so the index also catches the
 * reversed duplicate.
 */
@Getter
@Setter
@Entity
@Audited
@NoArgsConstructor
@Table(name = "relation_link")
public class RelationLink {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "relation_link_id", nullable = false)
    private Long id;

    @Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fk_relation_definition_id", nullable = false)
    private RelationDefinition definition;

    /**
     * The project both records belong to (a link never joins two projects): the link obeys the rules of this
     * project, else of its institution, else the platform default.
     */
    @Column(name = "fk_action_unit_id", nullable = false)
    private Long projectId;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_kind", nullable = false, length = 32)
    private RelationEndKind sourceKind;

    @Column(name = "source_id", nullable = false)
    private Long sourceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_kind", nullable = false, length = 32)
    private RelationEndKind targetKind;

    @Column(name = "target_id", nullable = false)
    private Long targetId;

    /** For a complex relation, the qualifier as seen from the source ("recouvre"); the target shows its mirror. */
    @Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fk_qualifier_concept_id")
    private Concept qualifier;

    @Column(name = "uncertain", nullable = false)
    private boolean uncertain;

    @Column(name = "simple", nullable = false)
    private boolean simple;
}
