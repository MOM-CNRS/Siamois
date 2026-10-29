package fr.siamois.infrastructure.database.initializer;

import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.models.vocabulary.Vocabulary;
import fr.siamois.domain.services.vocabulary.ConceptService;
import fr.siamois.domain.services.vocabulary.VocabularyService;
import fr.siamois.infrastructure.api.ConceptApi;
import fr.siamois.infrastructure.api.dto.FullInfoDTO;
import fr.siamois.infrastructure.api.dto.PurlInfoDTO;
import fr.siamois.infrastructure.database.repositories.vocabulary.ConceptRepository;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.ResourceAccessException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SystemFieldConceptInitializerTest {

    private final VocabularyService vocabularyService = mock(VocabularyService.class);
    private final ConceptRepository conceptRepository = mock(ConceptRepository.class);
    private final ConceptApi conceptApi = mock(ConceptApi.class);
    private final ConceptService conceptService = mock(ConceptService.class);
    private final SystemFieldConceptInitializer initializer =
            new SystemFieldConceptInitializer(vocabularyService, conceptRepository, conceptApi, conceptService);

    private Vocabulary storedVocabulary() throws Exception {
        Vocabulary vocabulary = new Vocabulary();
        vocabulary.setBaseUri("https://thesaurus.mom.fr");
        vocabulary.setExternalVocabularyId("th337");
        when(vocabularyService.findOrCreateVocabularyOfUri(any())).thenReturn(vocabulary);
        return vocabulary;
    }

    private static FullInfoDTO info() {
        PurlInfoDTO value = new PurlInfoDTO();
        value.setValue("1");
        FullInfoDTO info = new FullInfoDTO();
        info.setIdentifier(new PurlInfoDTO[]{value});
        info.setPrefLabel(new PurlInfoDTO[]{value});
        return info;
    }

    @Test
    void loadsTheConceptsTheDatabaseDoesNotHold() throws Exception {
        storedVocabulary();
        when(conceptRepository.findConceptByExternalIdIgnoreCase(any(), any())).thenReturn(Optional.empty());
        when(conceptApi.fetchConceptInfo(any(), any())).thenReturn(info());

        initializer.initialize();

        verify(conceptService, org.mockito.Mockito.atLeastOnce()).saveOrGetConceptFromFullDTO(any(), any(), eq(null));
    }

    @Test
    void skipsTheConceptsAlreadyLoaded() throws Exception {
        storedVocabulary();
        Concept loaded = new Concept();
        loaded.setLoaded(true);
        when(conceptRepository.findConceptByExternalIdIgnoreCase(any(), any())).thenReturn(Optional.of(loaded));

        initializer.initialize();

        verify(conceptApi, never()).fetchConceptInfo(any(), any());
    }

    @Test
    void anUnreachableThesaurusStopsAtTheFirstFailureWithoutFailingTheStartup() throws Exception {
        storedVocabulary();
        when(conceptRepository.findConceptByExternalIdIgnoreCase(any(), any())).thenReturn(Optional.empty());
        when(conceptApi.fetchConceptInfo(any(), any())).thenThrow(new ResourceAccessException("down"));

        assertDoesNotThrow(initializer::initialize);

        verify(conceptApi, times(1)).fetchConceptInfo(any(), any());
    }
}
