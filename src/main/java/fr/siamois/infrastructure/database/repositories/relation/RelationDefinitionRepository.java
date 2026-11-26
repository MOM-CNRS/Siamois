package fr.siamois.infrastructure.database.repositories.relation;

import fr.siamois.domain.models.relation.RelationDefinition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface RelationDefinitionRepository extends JpaRepository<RelationDefinition, Long> {

    /** The definition a relation field is a side of, whichever side. */
    @Query("select d from RelationDefinition d where d.sourceField.id = :fieldId or d.targetField.id = :fieldId")
    Optional<RelationDefinition> findByField(@Param("fieldId") Long fieldId);
}
