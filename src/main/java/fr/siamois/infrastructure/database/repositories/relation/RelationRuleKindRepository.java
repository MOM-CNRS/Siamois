package fr.siamois.infrastructure.database.repositories.relation;

import fr.siamois.domain.models.relation.RelationRuleKind;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface RelationRuleKindRepository extends JpaRepository<RelationRuleKind, Long> {

    List<RelationRuleKind> findByRuleId(Long ruleId);

    @Modifying
    @Query("delete from RelationRuleKind k where k.rule.id = :ruleId")
    void deleteByRuleId(@Param("ruleId") Long ruleId);
}
