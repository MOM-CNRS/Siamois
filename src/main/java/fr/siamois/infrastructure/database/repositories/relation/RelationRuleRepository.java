package fr.siamois.infrastructure.database.repositories.relation;

import fr.siamois.domain.models.relation.RelationDefinition;
import fr.siamois.domain.models.relation.RelationRule;
import fr.siamois.domain.models.relation.RelationRuleKind;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RelationRuleRepository extends JpaRepository<RelationRule, Long> {

    /** Every rule that applies to a definition (alone or together with others). */
    List<RelationRule> findByDefinitionsContaining(RelationDefinition definition);

    /** The rules of one kind that apply to a definition (alone or together with others). */
    List<RelationRule> findByKindAndDefinitionsContaining(RelationRuleKind kind, RelationDefinition definition);
}
