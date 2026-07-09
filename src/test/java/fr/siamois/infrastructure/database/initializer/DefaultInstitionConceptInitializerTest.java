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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DefaultInstitionConceptInitializerTest {

    @Mock
    private VocabularyService vocabularyService;
    @Mock
    private FieldConfigurationService fieldConfigurationService;
    @Mock
    private PersonService personService;
    @Mock
    private InstitutionService institutionService;

    @InjectMocks
    private DefaultInstitionConceptInitializer initializer;

    private Vocabulary vocabulary;

    @BeforeEach
    void setUp() {
        vocabulary = new Vocabulary();
    }

    @Test
    void initialize_success() throws Exception {
        when(vocabularyService.findOrCreateVocabularyOfUri(anyString())).thenReturn(vocabulary);
        when(personService.findByUsername("system")).thenReturn(Optional.of(new PersonDTO()));
        when(institutionService.findByIdentifier("siamois")).thenReturn(Optional.of(new InstitutionDTO()));
        when(fieldConfigurationService.setupFieldConfigurationForInstitution(any(UserInfo.class), any(Vocabulary.class)))
                .thenReturn(Optional.empty());

        assertDoesNotThrow(() -> initializer.initialize());

        verify(fieldConfigurationService).setupFieldConfigurationForInstitution(any(UserInfo.class), any(Vocabulary.class));
    }

    @Test
    void initialize_invalidEndpoint_throwsDataInitException() throws Exception {
        when(vocabularyService.findOrCreateVocabularyOfUri(anyString()))
                .thenThrow(new InvalidEndpointException("bad endpoint"));

        assertThrows(DatabaseDataInitException.class, () -> initializer.initialize());

        verify(personService, never()).findByUsername(anyString());
    }

    @Test
    void initialize_personNotFound_throwsDataInitException() throws Exception {
        when(vocabularyService.findOrCreateVocabularyOfUri(anyString())).thenReturn(vocabulary);
        when(personService.findByUsername("system")).thenReturn(Optional.empty());

        assertThrows(DatabaseDataInitException.class, () -> initializer.initialize());

        verify(institutionService, never()).findByIdentifier(anyString());
    }

    @Test
    void initialize_institutionNotFound_throwsDataInitException() throws Exception {
        when(vocabularyService.findOrCreateVocabularyOfUri(anyString())).thenReturn(vocabulary);
        when(personService.findByUsername("system")).thenReturn(Optional.of(new PersonDTO()));
        when(institutionService.findByIdentifier("siamois")).thenReturn(Optional.empty());

        assertThrows(DatabaseDataInitException.class, () -> initializer.initialize());
    }

    @Test
    void initialize_notSiamoisThesaurus_throwsDataInitException() throws Exception {
        when(vocabularyService.findOrCreateVocabularyOfUri(anyString())).thenReturn(vocabulary);
        when(personService.findByUsername("system")).thenReturn(Optional.of(new PersonDTO()));
        when(institutionService.findByIdentifier("siamois")).thenReturn(Optional.of(new InstitutionDTO()));
        when(fieldConfigurationService.setupFieldConfigurationForInstitution(any(UserInfo.class), any(Vocabulary.class)))
                .thenThrow(new NotSiamoisThesaurusException("not siamois"));

        assertThrows(DatabaseDataInitException.class, () -> initializer.initialize());
    }

    @Test
    void initialize_errorProcessingExpansion_throwsDataInitException() throws Exception {
        when(vocabularyService.findOrCreateVocabularyOfUri(anyString())).thenReturn(vocabulary);
        when(personService.findByUsername("system")).thenReturn(Optional.of(new PersonDTO()));
        when(institutionService.findByIdentifier("siamois")).thenReturn(Optional.of(new InstitutionDTO()));
        when(fieldConfigurationService.setupFieldConfigurationForInstitution(any(UserInfo.class), any(Vocabulary.class)))
                .thenThrow(new ErrorProcessingExpansionException("expansion error"));

        assertThrows(DatabaseDataInitException.class, () -> initializer.initialize());
    }
}
