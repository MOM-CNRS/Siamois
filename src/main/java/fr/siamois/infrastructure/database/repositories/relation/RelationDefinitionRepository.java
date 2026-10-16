package fr.siamois.infrastructure.database.repositories.relation;

import fr.siamois.domain.models.relation.RelationDefinition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RelationDefinitionRepository extends JpaRepository<RelationDefinition, Long> {

    Optional<RelationDefinition> findByCode(String code);
}
