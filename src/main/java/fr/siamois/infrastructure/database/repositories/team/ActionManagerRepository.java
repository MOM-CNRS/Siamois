package fr.siamois.infrastructure.database.repositories.team;

import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.institution.Institution;
import fr.siamois.domain.models.team.ActionManagerRelation;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.Set;

@Repository
public interface ActionManagerRepository extends CrudRepository<ActionManagerRelation, ActionManagerRelation.ActionManagerId> {
    Set<ActionManagerRelation> findAllByInstitutionId(Long id);

    Optional<ActionManagerRelation> findByPersonIdAndInstitutionId(Long personId, Long id);
}
