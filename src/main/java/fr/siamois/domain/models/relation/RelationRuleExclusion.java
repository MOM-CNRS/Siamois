package fr.siamois.domain.models.relation;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Under a rule set, a definition whose links cannot join the same pair as the rule's own definition, in either
 * direction (order and synchrony).
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "relation_rule_exclusion")
public class RelationRuleExclusion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "relation_rule_exclusion_id", nullable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fk_relation_rule_id", nullable = false)
    private RelationRule rule;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fk_excluded_definition_id", nullable = false)
    private RelationDefinition excludedDefinition;
}
