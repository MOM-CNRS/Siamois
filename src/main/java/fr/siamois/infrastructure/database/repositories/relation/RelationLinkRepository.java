package fr.siamois.infrastructure.database.repositories.relation;

import fr.siamois.domain.models.relation.RelationLink;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface RelationLinkRepository extends JpaRepository<RelationLink, Long> {

    boolean existsByDefinitionId(Long definitionId);

    @Query("select l from RelationLink l where l.definition.id = :definitionId and l.projectId in :projectIds")
    List<RelationLink> findOfProjects(@Param("definitionId") Long definitionId,
                                      @Param("projectIds") Collection<Long> projectIds);
}
