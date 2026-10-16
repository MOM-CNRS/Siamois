package fr.siamois.infrastructure.database.repositories.relation;

import fr.siamois.domain.models.relation.RelationDefinition;
import fr.siamois.domain.models.relation.RelationLink;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RelationLinkRepository extends JpaRepository<RelationLink, Long> {

    long countByDefinition(RelationDefinition definition);

    java.util.List<fr.siamois.domain.models.relation.RelationLink> findByDefinition(RelationDefinition definition);
}
