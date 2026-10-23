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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DefaultInstitionConceptInitializerTest {

    private static final String URL = "https://thesaurus.example.org/?idt=th1";

    @Mock
    private VocabularyService vocabularyService;
    @Mock
    private FieldConfigurationService fieldConfigurationService;
    @Mock
    private SiamoisThesaurus siamoisThesaurus;

    @InjectMocks
    private DefaultInstitionConceptInitializer initializer;

    private Vocabulary vocabulary;

    @BeforeEach
    void setUp() {
        vocabulary = new Vocabulary();
        when(siamoisThesaurus.url()).thenReturn(URL);
    }

    @Test
    void initialize_loadsTheFieldCodesOfTheSiamoisThesaurus() throws Exception {
        when(vocabularyService.findOrCreateVocabularyOfUri(URL)).thenReturn(vocabulary);
        when(fieldConfigurationService.loadFieldCodes(any(Vocabulary.class), any(ProgressWrapper.class)))
                .thenReturn(Optional.empty());

        assertDoesNotThrow(() -> initializer.initialize());

        verify(fieldConfigurationService).loadFieldCodes(any(Vocabulary.class), any(ProgressWrapper.class));
    }

    @Test
    void initialize_invalidEndpoint_throwsDataInitException() throws Exception {
        when(vocabularyService.findOrCreateVocabularyOfUri(anyString()))
                .thenThrow(new InvalidEndpointException("bad endpoint"));

        assertThrows(DatabaseDataInitException.class, () -> initializer.initialize());
    }

    @Test
    void initialize_notSiamoisThesaurus_throwsDataInitException() throws Exception {
        when(vocabularyService.findOrCreateVocabularyOfUri(anyString())).thenReturn(vocabulary);
        when(fieldConfigurationService.loadFieldCodes(any(Vocabulary.class), any(ProgressWrapper.class)))
                .thenThrow(new NotSiamoisThesaurusException("not siamois"));

        assertThrows(DatabaseDataInitException.class, () -> initializer.initialize());
    }

    @Test
    void initialize_errorProcessingExpansion_throwsDataInitException() throws Exception {
        when(vocabularyService.findOrCreateVocabularyOfUri(anyString())).thenReturn(vocabulary);
        when(fieldConfigurationService.loadFieldCodes(any(Vocabulary.class), any(ProgressWrapper.class)))
                .thenThrow(new ErrorProcessingExpansionException("expansion"));

        assertThrows(DatabaseDataInitException.class, () -> initializer.initialize());
    }
}
