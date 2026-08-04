package fr.siamois.domain.services.vocabulary;

import fr.siamois.domain.models.UserInfo;
import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.dto.entity.vocabulary.VocabularyDTO;
import fr.siamois.infrastructure.api.ConceptApi;
import fr.siamois.infrastructure.api.dto.LabelDTO;
import fr.siamois.infrastructure.api.dto.concept.ConceptApiCollectionDTO;
import fr.siamois.infrastructure.api.dto.concept.ConceptCollectionDetachedDTO;
import fr.siamois.infrastructure.database.repositories.vocabulary.ConceptCollectionRepository;
import fr.siamois.utils.context.ExecutionContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationContext;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConceptCollectionServiceTest {

    @Mock
    private ConceptCollectionRepository conceptCollectionRepository;

    @Mock
    private VocabularyService vocabularyService;

    @Mock
    private ConceptApi conceptApi;

    @Mock
    private ApplicationContext applicationContext;

    @InjectMocks
    private ConceptCollectionService conceptCollectionService;

    private VocabularyDTO vocabulary;

    @BeforeEach
    void setUp() {
        vocabulary = VocabularyDTO.builder()
                .baseUri("http://example.org")
                .externalVocabularyId("th1")
                .build();

        UserInfo userInfo = new UserInfo(new InstitutionDTO(), new PersonDTO(), "fr");
        userInfo.getInstitution().setId(12L);
        userInfo.getUser().setId(12L);
        ExecutionContextHolder.set(userInfo);
    }

    @AfterEach
    void tearDown() {
        ExecutionContextHolder.clear();
    }

    @Test
    void fetchCollectionsFromRemoteThesaurus_shouldLabelInTheLanguageOfTheUser() {
        when(conceptApi.fetchPublicCollections(vocabulary)).thenReturn(List.of(
                new ConceptApiCollectionDTO("g1", List.of(
                        new LabelDTO("en", "Pottery"),
                        new LabelDTO("fr", "Céramique")))
        ));

        List<ConceptCollectionDetachedDTO> results = conceptCollectionService.fetchCollectionsFromRemoteThesaurus(vocabulary);

        assertThat(results).hasSize(1);
        ConceptCollectionDetachedDTO collection = results.get(0);
        assertThat(collection.getLabelToDisplay()).isEqualTo("Céramique");
        assertThat(collection.getLabels()).hasSize(2);
        // it is a ConceptCollectionDTO, ready to be handed over to addConceptConfigFor
        assertThat(collection.getExternalId()).isEqualTo("g1");
        assertThat(collection.getVocabulary()).isEqualTo(vocabulary);
        assertThat(collection.getId()).isNull();
        assertThat(collection.getConcepts()).isNull();
    }

    @Test
    void fetchCollectionsFromRemoteThesaurus_shouldFallBackOnAnotherLanguage_whenTheUserOneIsMissing() {
        when(conceptApi.fetchPublicCollections(vocabulary)).thenReturn(List.of(
                new ConceptApiCollectionDTO("g1", List.of(new LabelDTO("en", "Pottery")))
        ));

        List<ConceptCollectionDetachedDTO> results = conceptCollectionService.fetchCollectionsFromRemoteThesaurus(vocabulary);

        assertThat(results.get(0).getLabelToDisplay()).isEqualTo("Pottery (en)");
    }

    @Test
    void fetchCollectionsFromRemoteThesaurus_shouldFallBackOnTheId_whenTheCollectionHasNoLabel() {
        when(conceptApi.fetchPublicCollections(vocabulary)).thenReturn(List.of(
                new ConceptApiCollectionDTO("g1", List.of()),
                new ConceptApiCollectionDTO("g2", null)
        ));

        List<ConceptCollectionDetachedDTO> results = conceptCollectionService.fetchCollectionsFromRemoteThesaurus(vocabulary);

        assertThat(results).extracting(ConceptCollectionDetachedDTO::getLabelToDisplay).containsExactly("g1", "g2");
        assertThat(results).allSatisfy(collection -> assertThat(collection.getLabels()).isEmpty());
    }

    @Test
    void fetchCollectionsFromRemoteThesaurus_shouldStillLabel_whenNoUserContextIsBound() {
        ExecutionContextHolder.clear();
        when(conceptApi.fetchPublicCollections(vocabulary)).thenReturn(List.of(
                new ConceptApiCollectionDTO("g1", List.of(new LabelDTO("fr", "Céramique")))
        ));

        List<ConceptCollectionDetachedDTO> results = conceptCollectionService.fetchCollectionsFromRemoteThesaurus(vocabulary);

        assertThat(results.get(0).getLabelToDisplay()).isEqualTo("Céramique (fr)");
    }

    @Test
    void fetchCollectionsFromRemoteThesaurus_shouldReturnEmptyList_whenTheThesaurusHasNoCollection() {
        when(conceptApi.fetchPublicCollections(vocabulary)).thenReturn(List.of());

        assertThat(conceptCollectionService.fetchCollectionsFromRemoteThesaurus(vocabulary)).isEmpty();
    }

}
