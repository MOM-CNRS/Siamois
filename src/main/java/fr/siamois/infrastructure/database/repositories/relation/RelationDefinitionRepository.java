package fr.siamois.infrastructure.database.repositories.relation;

import fr.siamois.domain.models.relation.RelationDefinition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RelationDefinitionRepository extends JpaRepository<RelationDefinition, Long> {

    Optional<RelationDefinition> findByCode(String code);

    /** The platform's definitions (no institution) and those of {@code institutionId}, system ones first. */
    @org.springframework.data.jpa.repository.Query("select d from RelationDefinition d where d.institution is null or d.institution.id = :institutionId order by d.system desc, d.code")
    java.util.List<RelationDefinition> findVisibleTo(@org.springframework.data.repository.query.Param("institutionId") Long institutionId);
}
