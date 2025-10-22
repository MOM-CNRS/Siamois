package fr.siamois.infrastructure.database.repositories.vocabulary;

import fr.siamois.domain.models.vocabulary.LocalizedConceptData;
import org.springframework.data.repository.CrudRepository;

public interface LocalizedConceptDataRepository extends CrudRepository<LocalizedConceptData, Long> {
}
