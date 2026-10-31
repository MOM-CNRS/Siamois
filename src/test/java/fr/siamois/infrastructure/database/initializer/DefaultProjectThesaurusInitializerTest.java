package fr.siamois.infrastructure.database.initializer;

import fr.siamois.domain.models.exceptions.api.InvalidEndpointException;
import fr.siamois.domain.models.misc.ProgressWrapper;
import fr.siamois.domain.models.vocabulary.Vocabulary;
import fr.siamois.domain.services.actionunit.DefaultProjectDefinition;
import fr.siamois.domain.services.vocabulary.FieldConfigurationService;
import fr.siamois.domain.services.vocabulary.VocabularyService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DefaultProjectThesaurusInitializerTest {

    private final DefaultProjectDefinition definition = new DefaultProjectDefinition();
    @Mock private VocabularyService vocabularyService;
    @Mock private FieldConfigurationService fieldConfigurationService;

    private DefaultProjectThesaurusInitializer initializer() {
        return new DefaultProjectThesaurusInitializer(definition, vocabularyService, fieldConfigurationService);
    }

    @Test
    void initialize_importsTheFieldCodesOfTheThesaurus() throws Exception {
        Vocabulary vocabulary = new Vocabulary();
        when(vocabularyService.findOrCreateVocabularyOfUri(definition.thesaurusUrl())).thenReturn(vocabulary);

        initializer().initialize();

        verify(fieldConfigurationService).loadFieldCodes(org.mockito.ArgumentMatchers.eq(vocabulary), any(ProgressWrapper.class));
    }

    @Test
    void initialize_doesNotStopTheApplicationWhenTheThesaurusIsUnreachable() throws Exception {
        when(vocabularyService.findOrCreateVocabularyOfUri(definition.thesaurusUrl())).thenThrow(new InvalidEndpointException("down"));

        assertThatCode(() -> initializer().initialize()).doesNotThrowAnyException();

        verify(fieldConfigurationService, never()).loadFieldCodes(any(), any());
    }
}
