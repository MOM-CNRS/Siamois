package fr.siamois.infrastructure.database.initializer;

import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.models.exceptions.ErrorProcessingExpansionException;
import fr.siamois.domain.models.exceptions.api.InvalidEndpointException;
import fr.siamois.domain.models.exceptions.api.NotSiamoisThesaurusException;
import fr.siamois.domain.models.exceptions.database.DatabaseDataInitException;
import fr.siamois.domain.models.vocabulary.Vocabulary;
import fr.siamois.domain.services.InstitutionService;
import fr.siamois.domain.services.person.PersonService;
import fr.siamois.domain.services.vocabulary.FieldConfigurationService;
import fr.siamois.domain.services.vocabulary.VocabularyService;
import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.dto.entity.PersonDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@RequiredArgsConstructor
@Component
public class DefaultInstitionConceptInitializer implements DatabaseInitializer {

    private static final String THESAURUS_URL = "https://thesaurus.mom.fr/?idt=th252";
    private final VocabularyService vocabularyService;
    private final FieldConfigurationService fieldConfigurationService;
    private final PersonService personService;
    private final InstitutionService institutionService;

    @Override
    public void initialize() throws DatabaseDataInitException {
        long begin = System.currentTimeMillis();
        Vocabulary vocabulary;
        try {
            vocabulary = vocabularyService.findOrCreateVocabularyOfUri(THESAURUS_URL);
        } catch (InvalidEndpointException e) {
            log.error("Invalid endpoint", e);
            throw new DatabaseDataInitException("Invalid endpoint", e);
        }
        PersonDTO personDTO = personService.findByUsername("system").orElseThrow(() -> new DatabaseDataInitException("Could not find admin user"));
        InstitutionDTO institutionDTO = institutionService.findByIdentifier("siamois").orElseThrow(() -> new DatabaseDataInitException("Could not find institution"));
        UserInfo userInfo = new UserInfo(institutionDTO, personDTO, "fr");
        try {
            fieldConfigurationService.setupFieldConfigurationForInstitution(userInfo, vocabulary);
            log.info("Default institution concept init completed in {} seconds", (System.currentTimeMillis() - begin)/1000);
        } catch (NotSiamoisThesaurusException e) {
            log.error("The specified thesaurus is not a siamois thesaurus", e);
            throw new DatabaseDataInitException("The specified thesaurus is not a siamois thesaurus", e);
        } catch (ErrorProcessingExpansionException e) {
            log.error("Could not set up field configuration for institution", e);
            throw new DatabaseDataInitException("Could not set up field configuration for institution", e);
        }
    }

}
