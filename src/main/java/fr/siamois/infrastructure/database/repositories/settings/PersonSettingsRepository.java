package fr.siamois.infrastructure.database.repositories.settings;

import fr.siamois.domain.models.settings.PersonSettings;
import org.springframework.data.repository.CrudRepository;

import java.util.Optional;

public interface PersonSettingsRepository extends CrudRepository<PersonSettings, Long> {
    Optional<PersonSettings> findByPersonId(Long personId);
}
