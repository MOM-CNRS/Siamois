package fr.siamois.domain.models.relation;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.HashSet;
import java.util.Set;

/**
 * A rule from the closed catalogue of {@link RelationRuleKind}, applying to one or more definitions.
 * Checked by the write service (no SQL constraint can span definitions); a link that breaks a rule
 * added afterwards is flagged, not deleted.
 */
@Entity
@Table(name = "relation_rule")
@Getter
@Setter
public class RelationRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "relation_rule_id", nullable = false)
    private Long id;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 30)
    private RelationRuleKind kind;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false, length = 10)
    private RelationRuleSeverity severity = RelationRuleSeverity.BLOCKING;

    /** The limit of a cardinality rule; unused by the other kinds. */
    @Column(name = "max_count")
    private Integer maxCount;

    @ManyToMany
    @JoinTable(name = "relation_rule_definition",
            joinColumns = @JoinColumn(name = "fk_relation_rule_id"),
            inverseJoinColumns = @JoinColumn(name = "fk_relation_definition_id"))
    private Set<RelationDefinition> definitions = new HashSet<>();
}
