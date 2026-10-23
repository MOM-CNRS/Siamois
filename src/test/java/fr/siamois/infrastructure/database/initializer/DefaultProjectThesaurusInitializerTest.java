package fr.siamois.infrastructure.database.initializer;

import fr.siamois.domain.models.exceptions.api.InvalidEndpointException;
import fr.siamois.domain.models.misc.ProgressWrapper;
import fr.siamois.domain.models.vocabulary.Vocabulary;
import fr.siamois.domain.services.actionunit.DefaultProjectDefinition;
import fr.siamois.domain.services.vocabulary.ConceptCollectionService;
import fr.siamois.domain.services.vocabulary.FieldConfigurationService;
import fr.siamois.domain.services.vocabulary.VocabularyService;
import fr.siamois.dto.entity.vocabulary.ConceptCollectionDTO;
import fr.siamois.dto.entity.vocabulary.VocabularyDTO;
import fr.siamois.mapper.vocabulary.VocabularyMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DefaultProjectThesaurusInitializerTest {

    private final DefaultProjectDefinition definition = new DefaultProjectDefinition();
    @Mock private VocabularyService vocabularyService;
    @Mock private VocabularyMapper vocabularyMapper;
    @Mock private FieldConfigurationService fieldConfigurationService;
    @Mock private ConceptCollectionService conceptCollectionService;

    private DefaultProjectThesaurusInitializer initializer() {
        return new DefaultProjectThesaurusInitializer(definition, vocabularyService, vocabularyMapper, fieldConfigurationService,
                conceptCollectionService);
    }

    @Test
    void initialize_importsTheFieldCodesAndEveryCollectionTheDefinitionReads() throws Exception {
        Vocabulary vocabulary = new Vocabulary();
        VocabularyDTO vocabularyDto = new VocabularyDTO();
        when(vocabularyService.findOrCreateVocabularyOfUri(definition.thesaurusUrl())).thenReturn(vocabulary);
        when(vocabularyMapper.convert(vocabulary)).thenReturn(vocabularyDto);

        initializer().initialize();

        verify(fieldConfigurationService).loadFieldCodes(org.mockito.ArgumentMatchers.eq(vocabulary), any(ProgressWrapper.class));
        ArgumentCaptor<ConceptCollectionDTO> collections = ArgumentCaptor.forClass(ConceptCollectionDTO.class);
        verify(conceptCollectionService, times(definition.collectionIds().size())).createOrUpdateConceptCollection(collections.capture());
        assertThat(collections.getAllValues()).extracting(ConceptCollectionDTO::getExternalId)
                .containsExactlyInAnyOrderElementsOf(definition.collectionIds());
        assertThat(collections.getAllValues()).allSatisfy(collection -> assertThat(collection.getVocabulary()).isSameAs(vocabularyDto));
    }

    @Test
    void initialize_doesNotStopTheApplicationWhenTheThesaurusIsUnreachable() throws Exception {
        when(vocabularyService.findOrCreateVocabularyOfUri(definition.thesaurusUrl())).thenThrow(new InvalidEndpointException("down"));

        assertThatCode(() -> initializer().initialize()).doesNotThrowAnyException();

        verify(fieldConfigurationService, never()).loadFieldCodes(any(), any());
        verify(conceptCollectionService, never()).createOrUpdateConceptCollection(any(ConceptCollectionDTO.class));
    }
}
