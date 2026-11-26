package fr.siamois.infrastructure.database.repositories.relation;

import fr.siamois.domain.models.relation.RelationRuleMirrorConcept;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface RelationRuleMirrorConceptRepository extends JpaRepository<RelationRuleMirrorConcept, Long> {

    List<RelationRuleMirrorConcept> findByRuleId(Long ruleId);

    @Modifying
    @Query("delete from RelationRuleMirrorConcept m where m.rule.id = :ruleId")
    void deleteByRuleId(@Param("ruleId") Long ruleId);
}
