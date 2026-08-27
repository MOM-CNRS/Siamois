package fr.siamois.domain.services.form;

import fr.siamois.domain.models.exceptions.ErrorProcessingExpansionException;
import fr.siamois.domain.models.exceptions.api.InvalidEndpointException;
import fr.siamois.domain.models.exceptions.vocabulary.VocabularyNotFoundException;
import fr.siamois.domain.models.form.config.ConceptFieldFormConfig;
import fr.siamois.domain.models.form.config.FieldFormConfig;
import fr.siamois.domain.models.form.config.FormConfig;
import fr.siamois.domain.models.form.customfield.vocabulary.CustomFieldSelectOne;
import fr.siamois.domain.models.vocabulary.*;
import fr.siamois.domain.services.vocabulary.ConceptCollectionService;
import fr.siamois.domain.services.vocabulary.ConceptService;
import fr.siamois.domain.services.vocabulary.VocabularyService;
import fr.siamois.dto.entity.vocabulary.ConceptCollectionDTO;
import fr.siamois.dto.entity.vocabulary.ConceptDTO;
import fr.siamois.dto.entity.vocabulary.VocabularyDTO;
import fr.siamois.infrastructure.api.ConceptApi;
import fr.siamois.infrastructure.api.dto.FullInfoDTO;
import fr.siamois.infrastructure.api.dto.PurlInfoDTO;
import fr.siamois.infrastructure.api.dto.concept.ConceptBranchDTO;
import fr.siamois.infrastructure.database.repositories.form.config.FieldFormConfigRepository;
import fr.siamois.infrastructure.database.repositories.vocabulary.ConceptHierarchyRepository;
import fr.siamois.infrastructure.database.repositories.vocabulary.ConceptRepository;
import fr.siamois.mapper.vocabulary.VocabularyMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationContext;

import java.util.HashSet;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FormConfigServiceTest {

    private static final String PARENT_URL = "https://thesaurus.example/parent";
    private static final String CHILD_URL = "https://thesaurus.example/child";
    private static final String RELATED_URL = "https://thesaurus.example/related";
    private static final String RELATED_URL_WITH_IDC = "https://thesaurus.example/?idt=th221&idc=4242";
    private static final String UNKNOWN_URL = "https://thesaurus.example/unknown";

    @Mock
    private FieldFormConfigRepository fieldFormConfigRepository;

    @Mock
    private ConceptService conceptService;

    @Mock
    private VocabularyService vocabularyService;

    @Mock
    private ConceptApi conceptApi;

    @Mock
    private ConceptHierarchyRepository conceptHierarchyRepository;

    @Mock
    private ConceptRepository conceptRepository;

    @Mock
    private VocabularyMapper vocabularyMapper;

    @Mock
    private ConceptCollectionService conceptCollectionService;

    @Mock
    private ApplicationContext applicationContext;

    @InjectMocks
    private FormConfigService formConfigService;

    private Vocabulary vocabulary;
    private VocabularyDTO vocabularyDTO;
    private ConceptDTO branchTopConceptDTO;
    private Concept branchTopConcept;
    private Concept childConcept;
    private FormConfig formConfig;
    private CustomFieldSelectOne field;

    @BeforeEach
    void setUp() {
        VocabularyType vocabularyType = new VocabularyType();
        vocabularyType.setId(1L);
        vocabularyType.setLabel("Thesaurus");

        vocabulary = new Vocabulary();
        vocabulary.setId(1L);
        vocabulary.setExternalVocabularyId("th221");
        vocabulary.setBaseUri("https://thesaurus.example");
        vocabulary.setType(vocabularyType);

        vocabularyDTO = VocabularyDTO.builder()
                .id(1L)
                .externalVocabularyId("th221")
                .baseUri("https://thesaurus.example")
                .build();

        branchTopConceptDTO = ConceptDTO.builder()
                .externalId("parent")
                .vocabulary(vocabularyDTO)
                .build();

        branchTopConcept = new Concept.Builder()
                .id(10L)
                .externalId("parent")
                .vocabulary(vocabulary)
                .build();
        branchTopConcept.setRelatedConcepts(new HashSet<>());

        childConcept = new Concept.Builder()
                .id(11L)
                .externalId("child")
                .vocabulary(vocabulary)
                .build();

        formConfig = new FormConfig();
        formConfig.setId(100L);

        field = new CustomFieldSelectOne();
        field.setId(200L);
        field.setLabel("Type de mobilier");
    }

    // --- Creation / reuse of the ConceptFieldFormConfig -----------------------------------------

    @Test
    void addConceptConfigFor_shouldCreateConfigWithTopTermAndNoCollection_whenNoConfigExists() throws Exception {
        stubDownExpansion(new ConceptBranchDTO());

        formConfigService.addConceptConfigFor(formConfig, field, branchTopConceptDTO);

        ConceptFieldFormConfig saved = captureSavedConfig();
        assertThat(saved.getFormConfig()).isSameAs(formConfig);
        assertThat(saved.getField()).isSameAs(field);
        assertThat(saved.getBranchTopTerm()).isSameAs(branchTopConcept);
        assertThat(saved.getCollection()).isNull();
        assertThat(saved.getId().getFormsConfigId()).isEqualTo(100L);
        assertThat(saved.getId().getCustomFieldId()).isEqualTo(200L);
    }

    @Test
    void addConceptConfigFor_shouldResolveVocabularyFromCompleteUri() throws Exception {
        stubDownExpansion(new ConceptBranchDTO());

        formConfigService.addConceptConfigFor(formConfig, field, branchTopConceptDTO);

        verify(vocabularyService).findOrCreateVocabularyOfUri("https://thesaurus.example?idt=th221");
        // the resolved vocabulary is handed back to the DTO before the concept is saved
        assertThat(branchTopConceptDTO.getVocabulary()).isSameAs(vocabularyDTO);
    }

    @Test
    void addConceptConfigFor_shouldReuseInstanceAndClearCollection_whenConceptConfigAlreadyExists() throws Exception {
        ConceptCollection previousCollection = new ConceptCollection();
        previousCollection.setId(5L);
        previousCollection.setExternalId("coll1");
        previousCollection.setVocabulary(vocabulary);

        Concept previousTopTerm = new Concept.Builder()
                .id(12L)
                .externalId("previousTopTerm")
                .vocabulary(vocabulary)
                .build();

        ConceptFieldFormConfig existing = new ConceptFieldFormConfig();
        existing.setFormConfig(formConfig);
        existing.setField(field);
        existing.setMandatory(true);
        existing.setInstitutionLocked(true);
        existing.setPosition(3);
        existing.setBranchTopTerm(previousTopTerm);
        existing.setCollection(previousCollection);

        when(fieldFormConfigRepository.findByFormConfigAndField(formConfig, field)).thenReturn(Optional.of(existing));
        stubVocabularyResolution();
        when(conceptService.saveOrGetConcept(branchTopConceptDTO)).thenReturn(branchTopConcept);
        when(conceptApi.fetchDownExpansion(vocabulary, "parent")).thenReturn(new ConceptBranchDTO());
        stubBranchLoadComponents();

        formConfigService.addConceptConfigFor(formConfig, field, branchTopConceptDTO);

        ConceptFieldFormConfig saved = captureSavedConfig();
        assertThat(saved).isSameAs(existing);
        assertThat(saved.getBranchTopTerm()).isSameAs(branchTopConcept);
        assertThat(saved.getCollection()).isNull();
        assertThat(saved.isMandatory()).isTrue();
        assertThat(saved.isInstitutionLocked()).isTrue();
        assertThat(saved.getPosition()).isEqualTo(3);
    }

    @Test
    void addConceptConfigFor_shouldConvertAndKeepAttributes_whenExistingConfigIsNotConceptTyped() throws Exception {
        FieldFormConfig existing = new FieldFormConfig();
        existing.setFormConfig(formConfig);
        existing.setField(field);
        existing.setActive(false);
        existing.setMandatory(true);
        existing.setInstitutionLocked(true);
        existing.setPosition(7);

        when(fieldFormConfigRepository.findByFormConfigAndField(formConfig, field)).thenReturn(Optional.of(existing));
        stubVocabularyResolution();
        when(conceptService.saveOrGetConcept(branchTopConceptDTO)).thenReturn(branchTopConcept);
        when(conceptApi.fetchDownExpansion(vocabulary, "parent")).thenReturn(new ConceptBranchDTO());
        stubBranchLoadComponents();

        formConfigService.addConceptConfigFor(formConfig, field, branchTopConceptDTO);

        ConceptFieldFormConfig saved = captureSavedConfig();
        assertThat(saved).isNotSameAs(existing);
        assertThat(saved.getId()).isSameAs(existing.getId());
        assertThat(saved.getFormConfig()).isSameAs(formConfig);
        assertThat(saved.getField()).isSameAs(field);
        assertThat(saved.isActive()).isFalse();
        assertThat(saved.isMandatory()).isTrue();
        assertThat(saved.isInstitutionLocked()).isTrue();
        assertThat(saved.getPosition()).isEqualTo(7);
        assertThat(saved.getBranchTopTerm()).isSameAs(branchTopConcept);
        assertThat(saved.getCollection()).isNull();
        // the stale plain FieldFormConfig must be removed (and the removal flushed) before the new
        // ConceptFieldFormConfig with the same id is saved, or Hibernate throws NonUniqueObjectException
        verify(fieldFormConfigRepository).delete(existing);
        verify(fieldFormConfigRepository).flush();
    }

    @Test
    void addConceptConfigFor_shouldThrowIllegalArgumentAndSaveNothing_whenVocabularyEndpointIsInvalid() throws Exception {
        when(fieldFormConfigRepository.findByFormConfigAndField(formConfig, field)).thenReturn(Optional.empty());
        when(vocabularyService.findOrCreateVocabularyOfUri(anyString()))
                .thenThrow(new InvalidEndpointException("invalid endpoint"));

        assertThatThrownBy(() -> formConfigService.addConceptConfigFor(formConfig, field, branchTopConceptDTO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("parent")
                .hasCauseInstanceOf(InvalidEndpointException.class);

        verify(fieldFormConfigRepository, never()).save(any());
        verifyNoInteractions(conceptApi, conceptHierarchyRepository);
    }

    // --- Down expansion ------------------------------------------------------------------------

    @Test
    void addConceptConfigFor_shouldSaveEveryConceptOfTheBranch() throws Exception {
        ConceptBranchDTO branch = branchWithParentAndChild();
        FullInfoDTO parentInfo = branch.getData().get(PARENT_URL);
        FullInfoDTO childInfo = branch.getData().get(CHILD_URL);

        stubDownExpansion(branch);
        when(conceptService.saveOrGetConceptFromFullDTO(vocabulary, parentInfo, null)).thenReturn(branchTopConcept);
        when(conceptService.saveOrGetConceptFromFullDTO(vocabulary, childInfo, null)).thenReturn(childConcept);

        formConfigService.addConceptConfigFor(formConfig, field, branchTopConceptDTO);

        verify(conceptService).saveOrGetConceptFromFullDTO(vocabulary, parentInfo, null);
        verify(conceptService).saveOrGetConceptFromFullDTO(vocabulary, childInfo, null);
        assertThat(captureSavedConfig().getBranchTopTerm()).isSameAs(branchTopConcept);
    }

    @Test
    void addConceptConfigFor_shouldCreateHierarchyRelation_forEachNarrowerConcept() throws Exception {
        ConceptBranchDTO branch = branchWithParentAndChild();
        FullInfoDTO parentInfo = branch.getData().get(PARENT_URL);
        FullInfoDTO childInfo = branch.getData().get(CHILD_URL);
        parentInfo.setNarrower(new PurlInfoDTO[]{purl(CHILD_URL)});

        stubDownExpansion(branch);
        when(conceptService.saveOrGetConceptFromFullDTO(vocabulary, parentInfo, null)).thenReturn(branchTopConcept);
        when(conceptService.saveOrGetConceptFromFullDTO(vocabulary, childInfo, null)).thenReturn(childConcept);

        formConfigService.addConceptConfigFor(formConfig, field, branchTopConceptDTO);

        ArgumentCaptor<ConceptHierarchy> captor = ArgumentCaptor.forClass(ConceptHierarchy.class);
        verify(conceptHierarchyRepository).save(captor.capture());
        assertThat(captor.getValue().getParent()).isSameAs(branchTopConcept);
        assertThat(captor.getValue().getChild()).isSameAs(childConcept);
        assertThat(captor.getValue().getParentFieldContext()).isNull();
    }

    @Test
    void addConceptConfigFor_shouldNotCreateHierarchyRelation_whenConceptIsItsOwnNarrower() throws Exception {
        ConceptBranchDTO branch = new ConceptBranchDTO.ConceptBranchDTOBuilder()
                .identifier(PARENT_URL, "parent")
                .build();
        FullInfoDTO parentInfo = branch.getData().get(PARENT_URL);
        parentInfo.setNarrower(new PurlInfoDTO[]{purl(PARENT_URL)});

        stubDownExpansion(branch);
        when(conceptService.saveOrGetConceptFromFullDTO(vocabulary, parentInfo, null)).thenReturn(branchTopConcept);

        formConfigService.addConceptConfigFor(formConfig, field, branchTopConceptDTO);

        verify(conceptHierarchyRepository, never()).save(any());
    }

    @Test
    void addConceptConfigFor_shouldSkipTheNarrowerRelation_whenTheChildConceptIsNotPartOfTheBranch() throws Exception {
        // a branch/collection is a subset of the thesaurus : a fetched concept can have a narrower
        // concept that belongs outside that subset, so it is simply absent from the response — not a
        // sign of corrupt data, and it must not abort saving the rest of the (still valid) branch
        ConceptBranchDTO branch = new ConceptBranchDTO.ConceptBranchDTOBuilder()
                .identifier(PARENT_URL, "parent")
                .build();
        FullInfoDTO parentInfo = branch.getData().get(PARENT_URL);
        parentInfo.setNarrower(new PurlInfoDTO[]{purl(UNKNOWN_URL)});

        stubDownExpansion(branch);
        when(conceptService.saveOrGetConceptFromFullDTO(vocabulary, parentInfo, null)).thenReturn(branchTopConcept);

        formConfigService.addConceptConfigFor(formConfig, field, branchTopConceptDTO);

        verify(conceptHierarchyRepository, never()).save(any());
        verify(fieldFormConfigRepository).save(any());
    }

    @Test
    void addConceptConfigFor_shouldAttachRelatedConceptsToTheSavedConcept_asAnUnloadedStub() throws Exception {
        ConceptBranchDTO branch = new ConceptBranchDTO.ConceptBranchDTOBuilder()
                .identifier(PARENT_URL, "parent")
                .build();
        FullInfoDTO parentInfo = branch.getData().get(PARENT_URL);
        parentInfo.setRelated(new PurlInfoDTO[]{purl(RELATED_URL)});

        Concept savedStub = new Concept.Builder()
                .id(13L)
                .vocabulary(vocabulary)
                .build();

        stubDownExpansion(branch);
        when(conceptService.saveOrGetConceptFromFullDTO(vocabulary, parentInfo, null)).thenReturn(branchTopConcept);
        when(conceptRepository.findByUri(RELATED_URL)).thenReturn(Optional.empty());
        when(conceptRepository.save(any(Concept.class))).thenReturn(savedStub);

        formConfigService.addConceptConfigFor(formConfig, field, branchTopConceptDTO);

        ArgumentCaptor<Concept> stubCaptor = ArgumentCaptor.forClass(Concept.class);
        verify(conceptRepository).save(stubCaptor.capture());
        assertThat(stubCaptor.getValue().getUri()).isEqualTo(RELATED_URL);
        assertThat(stubCaptor.getValue().isLoaded()).isFalse();
        assertThat(stubCaptor.getValue().getVocabulary()).isSameAs(vocabulary);
        verify(conceptRepository).addRelatedConceptIfAbsent(branchTopConcept.getId(), savedStub.getId());
        // The concept behind the link is only fetched when something actually reads it.
        verify(conceptApi, never()).fetchConceptInfoByUri(any(Vocabulary.class), anyString());
    }

    @Test
    void addConceptConfigFor_shouldReuseTheExistingConcept_whenTheRelatedConceptIsAlreadyImported() throws Exception {
        ConceptBranchDTO branch = new ConceptBranchDTO.ConceptBranchDTOBuilder()
                .identifier(PARENT_URL, "parent")
                .build();
        FullInfoDTO parentInfo = branch.getData().get(PARENT_URL);
        parentInfo.setRelated(new PurlInfoDTO[]{purl(RELATED_URL_WITH_IDC)});

        Concept alreadyImported = new Concept.Builder()
                .id(13L)
                .externalId("4242")
                .vocabulary(vocabulary)
                .build();

        stubDownExpansion(branch);
        when(conceptService.saveOrGetConceptFromFullDTO(vocabulary, parentInfo, null)).thenReturn(branchTopConcept);
        when(conceptRepository.findConceptByExternalIdIgnoreCase("th221", "4242")).thenReturn(Optional.of(alreadyImported));

        formConfigService.addConceptConfigFor(formConfig, field, branchTopConceptDTO);

        verify(conceptRepository).addRelatedConceptIfAbsent(branchTopConcept.getId(), alreadyImported.getId());
        verify(conceptRepository, never()).save(any(Concept.class));
    }

    @Test
    void addConceptConfigFor_shouldStillSaveConfig_whenDownExpansionFails() throws Exception {
        when(fieldFormConfigRepository.findByFormConfigAndField(formConfig, field)).thenReturn(Optional.empty());
        stubVocabularyResolution();
        when(conceptService.saveOrGetConcept(branchTopConceptDTO)).thenReturn(branchTopConcept);
        when(conceptApi.fetchDownExpansion(vocabulary, "parent"))
                .thenThrow(new ErrorProcessingExpansionException("expansion failed"));

        formConfigService.addConceptConfigFor(formConfig, field, branchTopConceptDTO);

        ConceptFieldFormConfig saved = captureSavedConfig();
        assertThat(saved.getBranchTopTerm()).isSameAs(branchTopConcept);
        assertThat(saved.getCollection()).isNull();
        verifyNoInteractions(conceptHierarchyRepository, conceptRepository);
    }

    // --- Configuration on a collection ---------------------------------------------------------

    @Test
    void addConceptConfigFor_shouldCreateConfigWithCollectionAndNoTopTerm_whenNoConfigExists() {
        ConceptCollectionDTO collectionDTO = collectionDTO();
        ConceptCollection savedCollection = savedCollection();

        when(fieldFormConfigRepository.findByFormConfigAndField(formConfig, field)).thenReturn(Optional.empty());
        when(conceptCollectionService.createOrUpdateConceptCollection(eq(collectionDTO), any())).thenReturn(savedCollection);

        formConfigService.addConceptConfigFor(formConfig, field, collectionDTO);

        ConceptFieldFormConfig saved = captureSavedConfig();
        assertThat(saved.getFormConfig()).isSameAs(formConfig);
        assertThat(saved.getField()).isSameAs(field);
        assertThat(saved.getCollection()).isSameAs(savedCollection);
        assertThat(saved.getBranchTopTerm()).isNull();
        assertThat(saved.getId().getFormsConfigId()).isEqualTo(100L);
        assertThat(saved.getId().getCustomFieldId()).isEqualTo(200L);
        // the collection import is the collection service business, the branch expansion is not used here
        verifyNoInteractions(conceptApi, vocabularyService, conceptService);
    }

    @Test
    void addConceptConfigFor_shouldReuseInstanceAndClearTopTerm_whenConceptConfigAlreadyExists() {
        ConceptCollectionDTO collectionDTO = collectionDTO();
        ConceptCollection savedCollection = savedCollection();

        ConceptFieldFormConfig existing = new ConceptFieldFormConfig();
        existing.setFormConfig(formConfig);
        existing.setField(field);
        existing.setMandatory(true);
        existing.setInstitutionLocked(true);
        existing.setPosition(3);
        existing.setBranchTopTerm(branchTopConcept);

        when(fieldFormConfigRepository.findByFormConfigAndField(formConfig, field)).thenReturn(Optional.of(existing));
        when(conceptCollectionService.createOrUpdateConceptCollection(eq(collectionDTO), any())).thenReturn(savedCollection);

        formConfigService.addConceptConfigFor(formConfig, field, collectionDTO);

        ConceptFieldFormConfig saved = captureSavedConfig();
        assertThat(saved).isSameAs(existing);
        assertThat(saved.getCollection()).isSameAs(savedCollection);
        // a field is configured on a branch or on a collection, never on both
        assertThat(saved.getBranchTopTerm()).isNull();
        assertThat(saved.isMandatory()).isTrue();
        assertThat(saved.isInstitutionLocked()).isTrue();
        assertThat(saved.getPosition()).isEqualTo(3);
    }

    @Test
    void addConceptConfigFor_shouldConvertAndKeepAttributes_whenExistingCollectionConfigIsNotConceptTyped() {
        ConceptCollectionDTO collectionDTO = collectionDTO();
        ConceptCollection savedCollection = savedCollection();

        FieldFormConfig existing = new FieldFormConfig();
        existing.setFormConfig(formConfig);
        existing.setField(field);
        existing.setActive(false);
        existing.setPosition(7);

        when(fieldFormConfigRepository.findByFormConfigAndField(formConfig, field)).thenReturn(Optional.of(existing));
        when(conceptCollectionService.createOrUpdateConceptCollection(eq(collectionDTO), any())).thenReturn(savedCollection);

        formConfigService.addConceptConfigFor(formConfig, field, collectionDTO);

        ConceptFieldFormConfig saved = captureSavedConfig();
        assertThat(saved).isNotSameAs(existing);
        assertThat(saved.getId()).isSameAs(existing.getId());
        assertThat(saved.isActive()).isFalse();
        assertThat(saved.getPosition()).isEqualTo(7);
        assertThat(saved.getCollection()).isSameAs(savedCollection);
        verify(fieldFormConfigRepository).delete(existing);
        verify(fieldFormConfigRepository).flush();
    }

    @Test
    void addConceptConfigFor_shouldNotSaveConfig_whenTheCollectionCannotBeImported() {
        ConceptCollectionDTO collectionDTO = collectionDTO();

        when(fieldFormConfigRepository.findByFormConfigAndField(formConfig, field)).thenReturn(Optional.empty());
        when(conceptCollectionService.createOrUpdateConceptCollection(eq(collectionDTO), any()))
                .thenThrow(new VocabularyNotFoundException("Vocabulary could not be found"));

        assertThatThrownBy(() -> formConfigService.addConceptConfigFor(formConfig, field, collectionDTO))
                .isInstanceOf(VocabularyNotFoundException.class);

        verify(fieldFormConfigRepository, never()).save(any());
    }

    // --- Reading / clearing the current configuration -------------------------------------------

    @Test
    void findConceptConfigFor_shouldReturnTheExistingConceptConfig() {
        ConceptFieldFormConfig existing = new ConceptFieldFormConfig();
        existing.setFormConfig(formConfig);
        existing.setField(field);
        existing.setBranchTopTerm(branchTopConcept);
        when(fieldFormConfigRepository.findByFormConfigAndField(formConfig, field)).thenReturn(Optional.of(existing));

        Optional<ConceptFieldFormConfig> found = formConfigService.findConceptConfigFor(formConfig, field);

        assertThat(found).contains(existing);
    }

    @Test
    void findConceptConfigFor_shouldBeEmptyWhenNoConfigExists() {
        when(fieldFormConfigRepository.findByFormConfigAndField(formConfig, field)).thenReturn(Optional.empty());

        assertThat(formConfigService.findConceptConfigFor(formConfig, field)).isEmpty();
    }

    @Test
    void findConceptConfigFor_shouldBeEmptyWhenTheExistingConfigIsNotConceptTyped() {
        FieldFormConfig existing = new FieldFormConfig();
        existing.setFormConfig(formConfig);
        existing.setField(field);
        when(fieldFormConfigRepository.findByFormConfigAndField(formConfig, field)).thenReturn(Optional.of(existing));

        assertThat(formConfigService.findConceptConfigFor(formConfig, field)).isEmpty();
    }

    @Test
    void clearConceptConfigFor_shouldNullOutBothBranchAndCollectionAndSave() {
        ConceptFieldFormConfig existing = new ConceptFieldFormConfig();
        existing.setFormConfig(formConfig);
        existing.setField(field);
        existing.setBranchTopTerm(branchTopConcept);
        when(fieldFormConfigRepository.findByFormConfigAndField(formConfig, field)).thenReturn(Optional.of(existing));

        formConfigService.clearConceptConfigFor(formConfig, field);

        ConceptFieldFormConfig saved = captureSavedConfig();
        assertThat(saved).isSameAs(existing);
        assertThat(saved.getBranchTopTerm()).isNull();
        assertThat(saved.getCollection()).isNull();
    }

    @Test
    void clearConceptConfigFor_shouldDoNothingWhenNoConfigExists() {
        when(fieldFormConfigRepository.findByFormConfigAndField(formConfig, field)).thenReturn(Optional.empty());

        formConfigService.clearConceptConfigFor(formConfig, field);

        verify(fieldFormConfigRepository, never()).save(any());
    }

    // --- Helpers -------------------------------------------------------------------------------

    private ConceptCollectionDTO collectionDTO() {
        ConceptCollectionDTO collectionDTO = new ConceptCollectionDTO();
        collectionDTO.setExternalId("coll1");
        collectionDTO.setVocabulary(vocabularyDTO);
        return collectionDTO;
    }

    private ConceptCollection savedCollection() {
        ConceptCollection savedCollection = new ConceptCollection();
        savedCollection.setId(5L);
        savedCollection.setExternalId("coll1");
        savedCollection.setVocabulary(vocabulary);
        return savedCollection;
    }

    /**
     * The service resolves the vocabulary of the top term before saving it, and hands the resolved
     * one back to the DTO through the mapper.
     */
    private void stubVocabularyResolution() throws InvalidEndpointException {
        when(vocabularyService.findOrCreateVocabularyOfUri("https://thesaurus.example?idt=th221")).thenReturn(vocabulary);
        when(vocabularyMapper.convert(vocabulary)).thenReturn(vocabularyDTO);
    }

    /**
     * The branch is loaded through {@link fr.siamois.utils.vocabulary.ConceptApiUtils.BranchLoadComponents},
     * which pulls its collaborators from the application context.
     */
    private void stubBranchLoadComponents() {
        when(applicationContext.getBean(ConceptApi.class)).thenReturn(conceptApi);
        when(applicationContext.getBean(ConceptService.class)).thenReturn(conceptService);
        when(applicationContext.getBean(ConceptRepository.class)).thenReturn(conceptRepository);
        when(applicationContext.getBean(ConceptHierarchyRepository.class)).thenReturn(conceptHierarchyRepository);
    }

    private ConceptFieldFormConfig captureSavedConfig() {
        ArgumentCaptor<FieldFormConfig> captor = ArgumentCaptor.forClass(FieldFormConfig.class);
        verify(fieldFormConfigRepository).save(captor.capture());
        assertThat(captor.getValue()).isInstanceOf(ConceptFieldFormConfig.class);
        return (ConceptFieldFormConfig) captor.getValue();
    }

    private void stubDownExpansion(ConceptBranchDTO branch) throws ErrorProcessingExpansionException, InvalidEndpointException {
        when(fieldFormConfigRepository.findByFormConfigAndField(formConfig, field)).thenReturn(Optional.empty());
        stubVocabularyResolution();
        when(conceptService.saveOrGetConcept(branchTopConceptDTO)).thenReturn(branchTopConcept);
        when(conceptApi.fetchDownExpansion(vocabulary, "parent")).thenReturn(branch);
        stubBranchLoadComponents();
    }

    private ConceptBranchDTO branchWithParentAndChild() {
        return new ConceptBranchDTO.ConceptBranchDTOBuilder()
                .identifier(PARENT_URL, "parent")
                .identifier(CHILD_URL, "child")
                .build();
    }

    private static PurlInfoDTO purl(String value) {
        PurlInfoDTO purlInfoDTO = new PurlInfoDTO();
        purlInfoDTO.setType("uri");
        purlInfoDTO.setValue(value);
        return purlInfoDTO;
    }

}
