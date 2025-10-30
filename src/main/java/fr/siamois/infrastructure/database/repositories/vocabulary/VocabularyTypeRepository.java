package fr.siamois.infrastructure.database.repositories.vocabulary;

import fr.siamois.domain.models.vocabulary.VocabularyType;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.history.RevisionRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface VocabularyTypeRepository extends CrudRepository<VocabularyType, Long>, RevisionRepository<VocabularyType, Long, Long> {

    /**
     * Find a vocabulary type by its label.
     * @param label The label of the vocabulary type
     * @return An optional containing the vocabulary type if found
     */
    Optional<VocabularyType> findVocabularyTypeByLabel(String label);

}

