package fr.siamois.domain.models.relation;

import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.domain.models.institution.Institution;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.envers.Audited;
import org.hibernate.envers.RelationTargetAuditMode;

/**
 * The rules of a relation in one scope (docs/modele-relations.md, §3.3): the platform default (no institution, no
 * project), an institution (no project) or a project. A link obeys the rules of its project, else of its
 * institution, else the platform default; a rule set is complete, the levels are never merged. Links always join two
 * records of the same project, so rules that differ from one project to another never contradict each other.
 * <p>
 * The accepted kinds, mirror pairs and exclusions are rows of {@link RelationRuleKind},
 * {@link RelationRuleMirrorConcept} and {@link RelationRuleExclusion}, which point to their rule set.
 */
@Getter
@Setter
@Entity
@Audited
@NoArgsConstructor
@Table(name = "relation_rule")
public class RelationRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "relation_rule_id", nullable = false)
    private Long id;

    @Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fk_relation_definition_id", nullable = false)
    private RelationDefinition definition;

    /** Null for the platform default. */
    @Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fk_institution_id")
    private Institution institution;

    /** Null for the platform default and for an institution's rules. */
    @Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fk_action_unit_id")
    private ActionUnit actionUnit;

    /** No cycle in the links of the definition, within a project. Always on for a hierarchical definition. */
    @Column(name = "no_cycle", nullable = false)
    private boolean noCycle;

    /** A record has at most one link on the source side (it points to one record at most). */
    @Column(name = "source_unique", nullable = false)
    private boolean sourceUnique;

    /** A record has at most one link on the target side (one record at most points to it). */
    @Column(name = "target_unique", nullable = false)
    private boolean targetUnique;

    public boolean isPlatformDefault() {
        return institution == null && actionUnit == null;
    }
}
