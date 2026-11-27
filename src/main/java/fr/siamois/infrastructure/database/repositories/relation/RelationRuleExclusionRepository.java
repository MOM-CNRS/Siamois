package fr.siamois.infrastructure.database.repositories.relation;

import fr.siamois.domain.models.relation.RelationRuleExclusion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface RelationRuleExclusionRepository extends JpaRepository<RelationRuleExclusion, Long> {

    List<RelationRuleExclusion> findByRuleId(Long ruleId);

    /** The definitions whose rule sets, in some scope, exclude this one: candidates for the reverse check. */
    @Query("select distinct e.rule.definition.id from RelationRuleExclusion e where e.excludedDefinition.id = :definitionId")
    List<Long> findDefinitionsExcluding(@Param("definitionId") Long definitionId);

    @Modifying
    @Query("delete from RelationRuleExclusion e where e.rule.id = :ruleId")
    void deleteByRuleId(@Param("ruleId") Long ruleId);
}
