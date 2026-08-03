package fr.siamois.domain.services.form;

import fr.siamois.domain.models.exceptions.ErrorProcessingExpansionException;
import fr.siamois.domain.models.exceptions.api.InvalidEndpointException;
import fr.siamois.domain.models.form.config.ConceptFieldFormConfig;
import fr.siamois.domain.models.form.config.FieldFormConfig;
import fr.siamois.domain.models.form.config.FormConfig;
import fr.siamois.domain.models.form.customfield.vocabulary.CustomFieldSelectOne;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.models.vocabulary.ConceptCollection;
import fr.siamois.domain.models.vocabulary.ConceptHierarchy;
import fr.siamois.domain.models.vocabulary.Vocabulary;
import fr.siamois.domain.models.vocabulary.VocabularyType;
import fr.siamois.domain.services.vocabulary.ConceptService;
import fr.siamois.domain.services.vocabulary.VocabularyService;
import fr.siamois.dto.entity.vocabulary.ConceptDTO;
import fr.siamois.dto.entity.vocabulary.VocabularyDTO;
import fr.siamois.infrastructure.api.ConceptApi;
import fr.siamois.infrastructure.api.dto.ConceptBranchDTO;
import fr.siamois.infrastructure.api.dto.FullInfoDTO;
import fr.siamois.infrastructure.api.dto.PurlInfoDTO;
import fr.siamois.infrastructure.database.repositories.form.config.FieldFormConfigRepository;
import fr.siamois.infrastructure.database.repositories.vocabulary.ConceptHierarchyRepository;
import fr.siamois.infrastructure.database.repositories.vocabulary.ConceptRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashSet;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FormConfigServiceTest {

    private static final String PARENT_URL = "https://thesaurus.example/parent";
    private static final String CHILD_URL = "https://thesaurus.example/child";
    private static final String RELATED_URL = "https://thesaurus.example/related";
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

    @InjectMocks
    private FormConfigService formConfigService;

    private Vocabulary vocabulary;
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

        VocabularyDTO vocabularyDTO = VocabularyDTO.builder()
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
        when(fieldFormConfigRepository.findByFormConfigAndField(formConfig, field)).thenReturn(Optional.empty());
        when(conceptService.saveOrGetConcept(branchTopConceptDTO)).thenReturn(branchTopConcept);
        when(conceptApi.fetchDownExpansion(vocabulary, "parent")).thenReturn(new ConceptBranchDTO());

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
        when(fieldFormConfigRepository.findByFormConfigAndField(formConfig, field)).thenReturn(Optional.empty());
        when(conceptService.saveOrGetConcept(branchTopConceptDTO)).thenReturn(branchTopConcept);
        when(conceptApi.fetchDownExpansion(vocabulary, "parent")).thenReturn(new ConceptBranchDTO());

        formConfigService.addConceptConfigFor(formConfig, field, branchTopConceptDTO);

        verify(vocabularyService).findOrCreateVocabularyOfUri("https://thesaurus.example?idt=th221");
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
        when(conceptService.saveOrGetConcept(branchTopConceptDTO)).thenReturn(branchTopConcept);
        when(conceptApi.fetchDownExpansion(vocabulary, "parent")).thenReturn(new ConceptBranchDTO());

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
        when(conceptService.saveOrGetConcept(branchTopConceptDTO)).thenReturn(branchTopConcept);
        when(conceptApi.fetchDownExpansion(vocabulary, "parent")).thenReturn(new ConceptBranchDTO());

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
    void addConceptConfigFor_shouldThrowIllegalState_whenNarrowerConceptIsNotPartOfTheBranch() throws Exception {
        ConceptBranchDTO branch = new ConceptBranchDTO.ConceptBranchDTOBuilder()
                .identifier(PARENT_URL, "parent")
                .build();
        FullInfoDTO parentInfo = branch.getData().get(PARENT_URL);
        parentInfo.setNarrower(new PurlInfoDTO[]{purl(UNKNOWN_URL)});

        stubDownExpansion(branch);
        when(conceptService.saveOrGetConceptFromFullDTO(vocabulary, parentInfo, null)).thenReturn(branchTopConcept);

        assertThatThrownBy(() -> formConfigService.addConceptConfigFor(formConfig, field, branchTopConceptDTO))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(UNKNOWN_URL);

        verify(fieldFormConfigRepository, never()).save(any());
    }

    @Test
    void addConceptConfigFor_shouldAttachRelatedConceptsToTheSavedConcept() throws Exception {
        ConceptBranchDTO branch = new ConceptBranchDTO.ConceptBranchDTOBuilder()
                .identifier(PARENT_URL, "parent")
                .build();
        FullInfoDTO parentInfo = branch.getData().get(PARENT_URL);
        parentInfo.setRelated(new PurlInfoDTO[]{purl(RELATED_URL)});

        FullInfoDTO relatedInfo = new FullInfoDTO();
        relatedInfo.setIdentifier(new PurlInfoDTO[]{purl("related")});
        Concept relatedConcept = new Concept.Builder()
                .id(13L)
                .externalId("related")
                .vocabulary(vocabulary)
                .build();

        stubDownExpansion(branch);
        when(conceptService.saveOrGetConceptFromFullDTO(vocabulary, parentInfo, null)).thenReturn(branchTopConcept);
        when(conceptApi.fetchConceptInfoByUri(vocabulary, RELATED_URL)).thenReturn(relatedInfo);
        when(conceptService.saveOrGetConceptFromFullDTO(vocabulary, relatedInfo, null)).thenReturn(relatedConcept);
        when(conceptRepository.save(any(Concept.class))).thenAnswer(invocation -> invocation.getArgument(0));

        formConfigService.addConceptConfigFor(formConfig, field, branchTopConceptDTO);

        assertThat(branchTopConcept.getRelatedConcepts()).containsExactly(relatedConcept);
        verify(conceptRepository).save(branchTopConcept);
    }

    @Test
    void addConceptConfigFor_shouldStillSaveConfig_whenDownExpansionFails() throws Exception {
        when(fieldFormConfigRepository.findByFormConfigAndField(formConfig, field)).thenReturn(Optional.empty());
        when(conceptService.saveOrGetConcept(branchTopConceptDTO)).thenReturn(branchTopConcept);
        when(conceptApi.fetchDownExpansion(vocabulary, "parent"))
                .thenThrow(new ErrorProcessingExpansionException("expansion failed"));

        formConfigService.addConceptConfigFor(formConfig, field, branchTopConceptDTO);

        ConceptFieldFormConfig saved = captureSavedConfig();
        assertThat(saved.getBranchTopTerm()).isSameAs(branchTopConcept);
        assertThat(saved.getCollection()).isNull();
        verifyNoInteractions(conceptHierarchyRepository, conceptRepository);
    }

    // --- Helpers -------------------------------------------------------------------------------

    private ConceptFieldFormConfig captureSavedConfig() {
        ArgumentCaptor<FieldFormConfig> captor = ArgumentCaptor.forClass(FieldFormConfig.class);
        verify(fieldFormConfigRepository).save(captor.capture());
        assertThat(captor.getValue()).isInstanceOf(ConceptFieldFormConfig.class);
        return (ConceptFieldFormConfig) captor.getValue();
    }

    private void stubDownExpansion(ConceptBranchDTO branch) throws ErrorProcessingExpansionException {
        when(fieldFormConfigRepository.findByFormConfigAndField(formConfig, field)).thenReturn(Optional.empty());
        when(conceptService.saveOrGetConcept(branchTopConceptDTO)).thenReturn(branchTopConcept);
        when(conceptApi.fetchDownExpansion(vocabulary, "parent")).thenReturn(branch);
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
