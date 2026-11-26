package fr.siamois.infrastructure.database.repositories.relation;

import fr.siamois.domain.models.relation.RelationRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RelationRuleRepository extends JpaRepository<RelationRule, Long> {

    @Query("select r from RelationRule r where r.definition.id = :definitionId and r.institution is null")
    Optional<RelationRule> findPlatformDefault(@Param("definitionId") Long definitionId);

    @Query("select r from RelationRule r where r.definition.id = :definitionId "
            + "and r.institution.id = :institutionId and r.actionUnit is null")
    Optional<RelationRule> findOfInstitution(@Param("definitionId") Long definitionId,
                                             @Param("institutionId") Long institutionId);

    @Query("select r from RelationRule r where r.definition.id = :definitionId and r.actionUnit.id = :projectId")
    Optional<RelationRule> findOfProject(@Param("definitionId") Long definitionId, @Param("projectId") Long projectId);

    List<RelationRule> findByDefinitionId(Long definitionId);

    @Query("select r from RelationRule r where r.actionUnit.id = :projectId")
    List<RelationRule> findAllOfProject(@Param("projectId") Long projectId);
}
