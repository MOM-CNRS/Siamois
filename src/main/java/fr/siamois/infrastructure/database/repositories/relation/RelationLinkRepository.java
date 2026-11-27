package fr.siamois.infrastructure.database.repositories.relation;

import fr.siamois.domain.models.relation.RelationLink;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface RelationLinkRepository extends JpaRepository<RelationLink, Long> {

    boolean existsByDefinitionId(Long definitionId);

    long countByDefinitionId(Long definitionId);

    /** The stratigraphic links (order, synchrony) between two recording units, either way. */
    @Query("select l from RelationLink l where l.definition.sourceField.id in (-328L, -330L) "
            + "and l.sourceKind = fr.siamois.domain.models.relation.RelationEndKind.RECORDING_UNIT "
            + "and l.targetKind = fr.siamois.domain.models.relation.RelationEndKind.RECORDING_UNIT "
            + "and ((l.sourceId = :a and l.targetId = :b) or (l.sourceId = :b and l.targetId = :a)) order by l.id")
    List<RelationLink> findStratigraphicBetween(@Param("a") Long a, @Param("b") Long b);

    /** The stratigraphic links (order, synchrony) whose source is one of these recording units. */
    @Query("select l from RelationLink l where l.definition.sourceField.id in (-328L, -330L) "
            + "and l.sourceKind = fr.siamois.domain.models.relation.RelationEndKind.RECORDING_UNIT and l.sourceId in :ids")
    List<RelationLink> findStratigraphicFromSources(@Param("ids") Collection<Long> ids);

    /** Every link a record is an end of, whatever its definition. */
    @Query("select l from RelationLink l where (l.sourceKind = :kind and l.sourceId = :id) or (l.targetKind = :kind and l.targetId = :id)")
    List<RelationLink> findAllOfRecord(@Param("kind") fr.siamois.domain.models.relation.RelationEndKind kind, @Param("id") Long id);

    @Query("select l from RelationLink l where l.definition.id = :definitionId and l.projectId in :projectIds")
    List<RelationLink> findOfProjects(@Param("definitionId") Long definitionId,
                                      @Param("projectIds") Collection<Long> projectIds);
}
