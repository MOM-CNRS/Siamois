package fr.siamois.infrastructure.database.repositories.permissions;

import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.permissions.PersonProfileAssignment;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PersonProfileAssignmentRepository extends CrudRepository<PersonProfileAssignment, PersonProfileAssignment.PersonProfileAssignmentId> {
}
