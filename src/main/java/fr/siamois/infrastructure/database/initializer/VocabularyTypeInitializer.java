package fr.siamois.infrastructure.database.initializer;

import fr.siamois.domain.models.exceptions.database.DatabaseDataInitException;
import fr.siamois.domain.models.vocabulary.VocabularyType;
import fr.siamois.infrastructure.database.repositories.vocabulary.VocabularyTypeRepository;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@Order(-1)
public class VocabularyTypeInitializer implements DatabaseInitializer {

    private final VocabularyTypeRepository vocabularyTypeRepository;

    public VocabularyTypeInitializer(VocabularyTypeRepository vocabularyTypeRepository) {
        this.vocabularyTypeRepository = vocabularyTypeRepository;
    }

    @Override
    public void initialize() throws DatabaseDataInitException {
        initTypeIfNotExist("Thesaurus");
        initTypeIfNotExist("Typology");
    }

    private void initTypeIfNotExist(String typeName) {
        Optional<VocabularyType> optThesaurus = vocabularyTypeRepository.findVocabularyTypeByLabel(typeName);
        if (optThesaurus.isEmpty()) {
            VocabularyType thesaurus = new VocabularyType();
            thesaurus.setLabel(typeName);
            vocabularyTypeRepository.save(thesaurus);
        }
    }

}
