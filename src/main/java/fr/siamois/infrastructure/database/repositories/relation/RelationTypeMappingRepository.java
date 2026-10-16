package fr.siamois.infrastructure.database.repositories.relation;

import fr.siamois.domain.models.relation.RelationDefinition;
import fr.siamois.domain.models.relation.RelationTypeMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RelationTypeMappingRepository extends JpaRepository<RelationTypeMapping, Long> {

    List<RelationTypeMapping> findByDefinition(RelationDefinition definition);
}
