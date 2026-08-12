package fr.siamois.domain.services.vocabulary;

import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.models.exceptions.ErrorProcessingExpansionException;
import fr.siamois.domain.models.exceptions.api.InvalidEndpointException;
import fr.siamois.domain.models.exceptions.vocabulary.VocabularyNotFoundException;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.models.vocabulary.ConceptCollection;
import fr.siamois.domain.models.vocabulary.Vocabulary;
import fr.siamois.domain.models.vocabulary.VocabularyType;
import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.dto.entity.vocabulary.ConceptCollectionDTO;
import fr.siamois.dto.entity.vocabulary.VocabularyDTO;
import fr.siamois.infrastructure.api.ConceptApi;
import fr.siamois.infrastructure.api.dto.LabelDTO;
import fr.siamois.infrastructure.api.dto.concept.ConceptApiCollectionDTO;
import fr.siamois.infrastructure.api.dto.concept.ConceptBranchDTO;
import fr.siamois.infrastructure.api.dto.concept.ConceptCollectionDetachedDTO;
import fr.siamois.infrastructure.database.repositories.vocabulary.ConceptCollectionRepository;
import fr.siamois.infrastructure.database.repositories.vocabulary.ConceptHierarchyRepository;
import fr.siamois.infrastructure.database.repositories.vocabulary.ConceptRepository;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

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

    /**
     * Pulled from the application context by the branch import, see {@link #stubBranchLoadComponents()}.
     */
    @Mock
    private ConceptService conceptService;

    @Mock
    private ConceptRepository conceptRepository;

    @Mock
    private ConceptHierarchyRepository conceptHierarchyRepository;

    @InjectMocks
    private ConceptCollectionService conceptCollectionService;

    private static final String CONCEPT_URL = "http://example.org/concept/th1/12";

    private VocabularyDTO vocabulary;
    private Vocabulary jpaVocabulary;

    @BeforeEach
    void setUp() {
        vocabulary = VocabularyDTO.builder()
                .baseUri("http://example.org")
                .externalVocabularyId("th1")
                .build();

        VocabularyType type = new VocabularyType();
        type.setId(1L);
        type.setLabel("Thesaurus");

        jpaVocabulary = new Vocabulary();
        jpaVocabulary.setId(1L);
        jpaVocabulary.setBaseUri("http://example.org");
        jpaVocabulary.setExternalVocabularyId("th1");
        jpaVocabulary.setType(type);

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

    // --- the collection a pasted URL designates -------------------------------------------------

    @Test
    void fetchCollectionDesignatedBy_shouldReadTheIdgOfTheUrl() {
        when(conceptApi.fetchPublicCollections(vocabulary)).thenReturn(List.of(
                new ConceptApiCollectionDTO("g173", List.of(new LabelDTO("fr", "P2-ENTITÉS NOMMÉES")))
        ));

        // the collection page of a thesaurus is an ordinary idg/idt URL
        Optional<ConceptCollectionDetachedDTO> result = conceptCollectionService
                .fetchCollectionDesignatedBy(vocabulary, "https://thesaurus.mom.fr/?idg=g173&idt=th277");

        assertThat(result).isPresent();
        assertThat(result.get().getExternalId()).isEqualTo("g173");
        assertThat(result.get().getLabelToDisplay()).isEqualTo("P2-ENTITÉS NOMMÉES");
        verify(conceptApi, never()).fetchGroupIdOfArk(anyString(), anyString());
    }

    @Test
    void fetchCollectionDesignatedBy_shouldIgnoreTheCaseOfTheId() {
        when(conceptApi.fetchPublicCollections(vocabulary)).thenReturn(List.of(
                new ConceptApiCollectionDTO("g173", List.of(new LabelDTO("fr", "P2-ENTITÉS NOMMÉES")))
        ));

        // resolving the ark of a collection lands on a page that hands the id back upper-cased,
        // while the API lists it lower-cased
        Optional<ConceptCollectionDetachedDTO> result = conceptCollectionService
                .fetchCollectionDesignatedBy(vocabulary, "https://pactols.frantiq.fr/?idg=G173&idt=TH_1");

        assertThat(result).isPresent();
        assertThat(result.get().getExternalId()).isEqualTo("g173");
    }

    @Test
    void fetchCollectionDesignatedBy_shouldResolveAnArkThroughTheApi() {
        when(conceptApi.fetchGroupIdOfArk("http://example.org", "ark:/26678/pcrt55mxscwskk"))
                .thenReturn(Optional.of("g173"));
        when(conceptApi.fetchPublicCollections(vocabulary)).thenReturn(List.of(
                new ConceptApiCollectionDTO("g173", List.of(new LabelDTO("fr", "P2-ENTITÉS NOMMÉES")))
        ));

        Optional<ConceptCollectionDetachedDTO> result = conceptCollectionService
                .fetchCollectionDesignatedBy(vocabulary, "http://example.org/ark:/26678/pcrt55mxscwskk");

        assertThat(result).isPresent();
        assertThat(result.get().getExternalId()).isEqualTo("g173");
    }

    @Test
    void fetchCollectionDesignatedBy_shouldBeEmpty_whenTheUrlDesignatesNoCollection() {
        assertThat(conceptCollectionService
                .fetchCollectionDesignatedBy(vocabulary, "https://pactols.frantiq.fr/?idc=246344&idt=TH_1")).isEmpty();

        verifyNoInteractions(conceptApi);
    }

    @Test
    void fetchCollectionDesignatedBy_shouldBeEmpty_whenTheThesaurusFails() {
        when(conceptApi.fetchGroupIdOfArk(anyString(), anyString())).thenThrow(new IllegalStateException("boom"));

        assertThat(conceptCollectionService
                .fetchCollectionDesignatedBy(vocabulary, "http://example.org/ark:/26678/unknown")).isEmpty();
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

    // --- Import of a collection ----------------------------------------------------------------

    @Test
    void createOrUpdateConceptCollection_shouldCreateTheCollection_whenItIsNotImportedYet() throws Exception {
        ConceptCollectionDTO detached = detachedCollection();
        stubVocabularyResolution();
        when(conceptCollectionRepository.findByVocabularyAndExternalId(jpaVocabulary, "g1")).thenReturn(Optional.empty());
        when(conceptCollectionRepository.save(any(ConceptCollection.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(conceptApi.fetchCollectionBranch(eq(jpaVocabulary), any(ConceptCollection.class))).thenReturn(null);

        ConceptCollection result = conceptCollectionService.createOrUpdateConceptCollection(detached);

        assertThat(result.getExternalId()).isEqualTo("g1");
        assertThat(result.getVocabulary()).isSameAs(jpaVocabulary);
        verify(conceptCollectionRepository).save(result);
    }

    @Test
    void createOrUpdateConceptCollection_shouldReuseTheCollection_whenItIsAlreadyImported() throws Exception {
        ConceptCollectionDTO detached = detachedCollection();
        ConceptCollection existing = existingCollection();

        stubVocabularyResolution();
        when(conceptCollectionRepository.findByVocabularyAndExternalId(jpaVocabulary, "g1")).thenReturn(Optional.of(existing));
        when(conceptApi.fetchCollectionBranch(jpaVocabulary, existing)).thenReturn(null);

        ConceptCollection result = conceptCollectionService.createOrUpdateConceptCollection(detached);

        assertThat(result).isSameAs(existing);
        // the collection already exists, it is only saved again when its concepts changed
        verify(conceptCollectionRepository, never()).save(any());
    }

    @Test
    void createOrUpdateConceptCollection_shouldThrowVocabularyNotFound_whenTheEndpointIsInvalid() throws Exception {
        ConceptCollectionDTO detached = detachedCollection();
        when(vocabularyService.findOrCreateVocabularyOfUri("http://example.org?idt=th1"))
                .thenThrow(new InvalidEndpointException("invalid endpoint"));

        assertThatThrownBy(() -> conceptCollectionService.createOrUpdateConceptCollection(detached))
                .isInstanceOf(VocabularyNotFoundException.class);

        verifyNoInteractions(conceptCollectionRepository, conceptApi);
    }

    @Test
    void createOrUpdateConceptCollection_shouldImportEveryConceptOfTheBranch() throws Exception {
        ConceptCollectionDTO detached = detachedCollection();
        ConceptCollection existing = existingCollection();

        stubVocabularyResolution();
        when(conceptCollectionRepository.findByVocabularyAndExternalId(jpaVocabulary, "g1")).thenReturn(Optional.of(existing));

        ConceptBranchDTO branch = new ConceptBranchDTO.ConceptBranchDTOBuilder()
                .identifier(CONCEPT_URL, "12")
                .build();
        when(conceptApi.fetchCollectionBranch(jpaVocabulary, existing)).thenReturn(branch);

        Concept importedConcept = new Concept.Builder()
                .id(30L)
                .externalId("12")
                .vocabulary(jpaVocabulary)
                .build();
        stubBranchLoadComponents();
        when(conceptService.saveOrGetConceptFromFullDTO(jpaVocabulary, branch.getData().get(CONCEPT_URL), null))
                .thenReturn(importedConcept);

        ConceptCollection result = conceptCollectionService.createOrUpdateConceptCollection(detached);

        assertThat(result.getConcepts()).containsExactly(importedConcept);
        verify(conceptCollectionRepository).save(existing);
    }

    @Test
    void createOrUpdateConceptCollection_shouldKeepTheCollectionUntouched_whenItHasNotChangedRemotely() throws Exception {
        ConceptCollectionDTO detached = detachedCollection();
        ConceptCollection existing = existingCollection();
        existing.getConcepts().add(new Concept.Builder().id(30L).externalId("12").vocabulary(jpaVocabulary).build());

        stubVocabularyResolution();
        when(conceptCollectionRepository.findByVocabularyAndExternalId(jpaVocabulary, "g1")).thenReturn(Optional.of(existing));
        // a null branch means the checksum did not move: nothing to re-import
        when(conceptApi.fetchCollectionBranch(jpaVocabulary, existing)).thenReturn(null);

        ConceptCollection result = conceptCollectionService.createOrUpdateConceptCollection(detached);

        assertThat(result.getConcepts()).hasSize(1);
        verify(conceptCollectionRepository, never()).save(any());
        verifyNoInteractions(applicationContext);
    }

    @Test
    void createOrUpdateConceptCollection_shouldStillReturnTheCollection_whenTheBranchCannotBeFetched() throws Exception {
        ConceptCollectionDTO detached = detachedCollection();
        ConceptCollection existing = existingCollection();

        stubVocabularyResolution();
        when(conceptCollectionRepository.findByVocabularyAndExternalId(jpaVocabulary, "g1")).thenReturn(Optional.of(existing));
        when(conceptApi.fetchCollectionBranch(jpaVocabulary, existing))
                .thenThrow(new ErrorProcessingExpansionException("branch not found"));

        ConceptCollection result = conceptCollectionService.createOrUpdateConceptCollection(detached);

        assertThat(result).isSameAs(existing);
        assertThat(result.getConcepts()).isEmpty();
        verify(conceptCollectionRepository, never()).save(any());
    }

    // --- Helpers -------------------------------------------------------------------------------

    private ConceptCollectionDTO detachedCollection() {
        ConceptCollectionDTO detached = new ConceptCollectionDTO();
        detached.setExternalId("g1");
        detached.setVocabulary(vocabulary);
        return detached;
    }

    private ConceptCollection existingCollection() {
        ConceptCollection existing = new ConceptCollection();
        existing.setId(5L);
        existing.setExternalId("g1");
        existing.setVocabulary(jpaVocabulary);
        return existing;
    }

    private void stubVocabularyResolution() throws InvalidEndpointException {
        when(vocabularyService.findOrCreateVocabularyOfUri("http://example.org?idt=th1")).thenReturn(jpaVocabulary);
    }

    /**
     * The branch is imported through {@link fr.siamois.utils.vocabulary.ConceptApiUtils.BranchLoadComponents},
     * which pulls its collaborators from the application context.
     */
    private void stubBranchLoadComponents() {
        when(applicationContext.getBean(ConceptApi.class)).thenReturn(conceptApi);
        when(applicationContext.getBean(ConceptService.class)).thenReturn(conceptService);
        when(applicationContext.getBean(ConceptRepository.class)).thenReturn(conceptRepository);
        when(applicationContext.getBean(ConceptHierarchyRepository.class)).thenReturn(conceptHierarchyRepository);
    }

}
