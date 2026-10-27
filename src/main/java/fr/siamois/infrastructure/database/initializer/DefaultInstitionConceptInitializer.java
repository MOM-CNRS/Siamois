package fr.siamois.infrastructure.database.initializer;

import fr.siamois.domain.models.exceptions.ErrorProcessingExpansionException;
import fr.siamois.domain.models.exceptions.api.InvalidEndpointException;
import fr.siamois.domain.models.exceptions.api.NotSiamoisThesaurusException;
import fr.siamois.domain.models.exceptions.database.DatabaseDataInitException;
import fr.siamois.domain.models.misc.ProgressWrapper;
import fr.siamois.domain.models.vocabulary.Vocabulary;
import fr.siamois.domain.services.vocabulary.FieldConfigurationService;
import fr.siamois.domain.services.vocabulary.SiamoisThesaurus;
import fr.siamois.domain.services.vocabulary.VocabularyService;
import fr.siamois.infrastructure.database.repositories.institution.InstitutionRepository;
import fr.siamois.mapper.InstitutionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * Imports the field codes of the SIAMOIS thesaurus when the application starts, and makes it the documentation
 * thesaurus of the default organisation while that one has none (an empty database).
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class DefaultInstitionConceptInitializer implements DatabaseInitializer {

    private final VocabularyService vocabularyService;
    private final FieldConfigurationService fieldConfigurationService;
    private final SiamoisThesaurus siamoisThesaurus;
    private final InstitutionRepository institutionRepository;
    private final InstitutionMapper institutionMapper;

    @Override
    public void initialize() throws DatabaseDataInitException {
        long begin = System.currentTimeMillis();
        Vocabulary vocabulary;
        try {
            vocabulary = vocabularyService.findOrCreateVocabularyOfUri(siamoisThesaurus.url());
        } catch (InvalidEndpointException e) {
            log.error("Invalid endpoint", e);
            throw new DatabaseDataInitException("Invalid endpoint", e);
        }
        try {
            fieldConfigurationService.loadFieldCodes(vocabulary, new ProgressWrapper());
            log.info("Default institution concept init completed in {} seconds", (System.currentTimeMillis() - begin)/1000);
        } catch (NotSiamoisThesaurusException e) {
            log.error("The specified thesaurus is not a siamois thesaurus", e);
            throw new DatabaseDataInitException("The specified thesaurus is not a siamois thesaurus", e);
        } catch (ErrorProcessingExpansionException e) {
            log.error("Could not set up field configuration for institution", e);
            throw new DatabaseDataInitException("Could not set up field configuration for institution", e);
        }

        // SystemUserDatasetInitializer (ordered first) has created the default organisation
        institutionRepository.findInstitutionByIdentifier(SystemUserDatasetInitializer.DEFAULT_INSTITUTION_IDENTIFIER)
                .ifPresent(institution -> fieldConfigurationService.setDocumentationThesaurusIfAbsent(
                        Objects.requireNonNull(institutionMapper.convert(institution)), vocabulary));
    }

}
