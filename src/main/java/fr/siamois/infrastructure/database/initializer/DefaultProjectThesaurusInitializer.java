package fr.siamois.infrastructure.database.initializer;

import fr.siamois.domain.models.misc.ProgressWrapper;
import fr.siamois.domain.models.vocabulary.Vocabulary;
import fr.siamois.domain.services.actionunit.DefaultProjectDefinition;
import fr.siamois.domain.services.vocabulary.FieldConfigurationService;
import fr.siamois.domain.services.vocabulary.VocabularyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Imports, when the application starts, the thesaurus the "default project" is made of: its field codes (so the types and
 * the lists of the fields, which the thesaurus relates to the types). A new project is then configured from the local
 * database alone.
 * <p>Unlike the SIAMOIS thesaurus, this one is not needed to run: an unreachable thesaurus is logged, and a project created
 * from the default template then starts without what it should have read from it.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultProjectThesaurusInitializer implements DatabaseInitializer {

    private final DefaultProjectDefinition definition;
    private final VocabularyService vocabularyService;
    private final FieldConfigurationService fieldConfigurationService;

    @Override
    public void initialize() {
        long begin = System.currentTimeMillis();
        try {
            Vocabulary vocabulary = vocabularyService.findOrCreateVocabularyOfUri(definition.thesaurusUrl());
            fieldConfigurationService.loadFieldCodes(vocabulary, new ProgressWrapper());
            log.info("Default project thesaurus {} imported in {} seconds", definition.thesaurusUrl(), (System.currentTimeMillis() - begin) / 1000);
        } catch (Exception e) {
            log.error("Could not import the default project thesaurus {}: new default projects will start without it",
                    definition.thesaurusUrl(), e);
        }
    }
}
