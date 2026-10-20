package fr.siamois.infrastructure.database.repositories;

import fr.siamois.domain.models.UserHistoryEntry;
import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.institution.Institution;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UserHistoryRepository extends CrudRepository<UserHistoryEntry, Long> {

    List<UserHistoryEntry> findByPersonAndInstitutionAndMainUriAndSecondaryUri(
            Person person, Institution institution, String mainUri, String secondaryUri);

    /** The user's entries in an organisation, the most recent first. */
    List<UserHistoryEntry> findByPersonAndInstitutionOrderByVisitedAtDescIdDesc(
            Person person, Institution institution, Pageable pageable);

    long countByPersonAndInstitution(Person person, Institution institution);
}
