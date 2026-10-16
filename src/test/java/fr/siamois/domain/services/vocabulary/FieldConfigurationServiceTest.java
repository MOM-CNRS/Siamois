package fr.siamois.domain.services.vocabulary;

import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.domain.models.exceptions.ErrorProcessingExpansionException;
import fr.siamois.domain.models.exceptions.api.NotSiamoisThesaurusException;
import fr.siamois.domain.models.exceptions.vocabulary.NoConfigForFieldException;
import fr.siamois.domain.models.form.config.ConceptFieldFormConfig;
import fr.siamois.domain.models.form.config.ConceptFieldState;
import fr.siamois.domain.models.form.config.ConceptState;
import fr.siamois.domain.models.form.config.FormConfig;
import fr.siamois.domain.models.form.config.VocabularyMode;
import fr.siamois.domain.models.form.customfield.vocabulary.CustomFieldSelectOne;
import fr.siamois.domain.models.form.customfield.vocabulary.CustomFieldSelectOneFromFieldCode;
import fr.siamois.domain.models.misc.ProgressWrapper;
import fr.siamois.domain.models.settings.ConceptFieldConfig;
import fr.siamois.domain.models.spatialunit.SpatialUnit;
import fr.siamois.domain.models.vocabulary.*;
import fr.siamois.dto.entity.ActionUnitDTO;
import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.dto.entity.vocabulary.ConceptDTO;
import fr.siamois.infrastructure.api.ConceptApi;
import fr.siamois.infrastructure.api.dto.FullInfoDTO;
import fr.siamois.infrastructure.api.dto.concept.ConceptBranchDTO;
import fr.siamois.infrastructure.database.repositories.form.config.ConceptFieldStateRepository;
import fr.siamois.infrastructure.database.repositories.form.config.FieldFormConfigRepository;
import fr.siamois.infrastructure.database.repositories.vocabulary.AutocompleteRepository;
import fr.siamois.infrastructure.database.repositories.vocabulary.ConceptFieldConfigRepository;
import fr.siamois.infrastructure.database.repositories.vocabulary.ConceptRepository;
import fr.siamois.infrastructure.database.repositories.vocabulary.dto.ConceptAutocompleteDTO;
import fr.siamois.mapper.ActionUnitMapper;
import fr.siamois.mapper.InstitutionMapper;
import fr.siamois.mapper.PersonMapper;
import fr.siamois.utils.context.ExecutionContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FieldConfigurationServiceTest {

    @Mock
    private ConceptApi conceptApi;
    @Mock
    private FieldService fieldService;
    @Mock
    private ConceptRepository conceptRepository;
    @Mock
    private ConceptService conceptService;

    @Mock
    private LabelService labelService;

    @Mock
    private AutocompleteRepository autocompleteRepository;

    @Mock
    private ConceptFieldConfigRepository conceptFieldConfigRepository;

    @Mock
    private InstitutionMapper institutionMapper;

    @Mock
    private ActionUnitMapper actionUnitMapper;

    @Mock
    private PersonMapper personMapper;

    @Mock
    private FieldFormConfigRepository fieldFormConfigRepository;

    @Mock
    private ConceptFieldStateRepository conceptFieldStateRepository;

    @Mock
    private ObjectProvider<FieldConfigurationService> selfProvider;

    @InjectMocks
    private FieldConfigurationService service;

    private UserInfo userInfo;
    private Vocabulary vocabulary;

    ConceptBranchDTO conceptBranchDTO;

    @BeforeEach
    void beforeEach() {

        VocabularyType type = new VocabularyType();
        type.setId(-1L);
        type.setLabel("Thesaurus");

        vocabulary = new Vocabulary();
        vocabulary.setId(-1L);
        vocabulary.setType(type);
        vocabulary.setExternalVocabularyId("th2");
        vocabulary.setBaseUri("http://exemple.org");

        userInfo = new UserInfo(new InstitutionDTO(), new PersonDTO(), "fr");
        userInfo.getInstitution().setId(12L);
        userInfo.getUser().setId(12L);

        conceptBranchDTO = new ConceptBranchDTO.ConceptBranchDTOBuilder()
                .identifier("http://exemple.org/concept/th2/1", "12")
                .label("http://exemple.org/concept/th2/1", "Label 12", "fr")
                .notation("http://exemple.org/concept/th2/1", "SIAMOIS#SIATEST")
                .notation("http://exemple.org/concept/th2/2", "SIAMOIS#SIAAUTO")
                .identifier("http://exemple.org/concept/th2/2", "122")
                .notation("http://exemple.org/concept/th2/3", "SIAMOIS#SIATEST2")
                .identifier("http://exemple.org/concept/th2/3", "123")
                .build();

        // fetchAutocomplete(CustomFieldConcept, ...) reads the current user from the execution context
        ExecutionContextHolder.set(userInfo);

        lenient().when(selfProvider.getObject()).thenReturn(service);
    }

    @AfterEach
    void afterEach() {
        ExecutionContextHolder.clear();
    }

    @Test
    void setupFieldConfigurationForInstitution_shouldReturnFieldConfigIfWrong_whenMissingFieldCodes() throws NotSiamoisThesaurusException, ErrorProcessingExpansionException {
        when(conceptApi.fetchFieldsBranch(vocabulary)).thenReturn(conceptBranchDTO);
        when(fieldService.searchAllFieldCodes()).thenReturn(List.of("SIATEST", "SIATEST2", "SIATEST3"));

        Optional<FeedbackFieldConfig> result = service.setupFieldConfigurationForInstitution(userInfo, vocabulary);

        assertThat(result).isPresent();
    }

    @Test
    void setupFieldConfigurationForInstitution_shouldReturnEmptyWhenValid_andConfigDoesNotExist() throws NotSiamoisThesaurusException, ErrorProcessingExpansionException {
        when(conceptApi.fetchFieldsBranch(vocabulary)).thenReturn(conceptBranchDTO);
        when(fieldService.searchAllFieldCodes()).thenReturn(List.of("SIATEST", "SIATEST2"));
        doAnswer(i -> {
            Vocabulary vocab = i.getArgument(0);
            FullInfoDTO dto = i.getArgument(1);
            Concept concept = new Concept();
            concept.setVocabulary(vocab);
            concept.setExternalId(dto.getIdentifier()[0].getValue());
            return concept;
        }).when(conceptService).saveOrGetConceptFromFullDTO(any(Vocabulary.class), any(FullInfoDTO.class), eq(null));
        when(conceptFieldConfigRepository.findOneByFieldCodeForInstitution(eq(userInfo.getInstitution().getId()), anyString())).thenReturn(Optional.empty());
        when(conceptFieldConfigRepository.save(any(ConceptFieldConfig.class))).thenAnswer(i -> i.getArgument(0));

        Optional<FeedbackFieldConfig> result = service.setupFieldConfigurationForInstitution(userInfo, vocabulary);

        assertThat(result).isEmpty();
        verify(conceptFieldConfigRepository, times(2)).save(any(ConceptFieldConfig.class));
        verify(conceptService, times(2)).saveAllSubConceptOfIfUpdated(any(ConceptFieldConfig.class), any(ProgressWrapper.class));
    }

    @Test
    void setupFieldConfigurationForInstitution_shouldThrowNotSiamoisThesaurusException_whenSIAAUTOCOMPLETEmissing() throws NotSiamoisThesaurusException, ErrorProcessingExpansionException {
        when(conceptApi.fetchFieldsBranch(vocabulary)).thenThrow(NotSiamoisThesaurusException.class);
        assertThrows(NotSiamoisThesaurusException.class, () -> service.setupFieldConfigurationForInstitution(userInfo, vocabulary));
    }

    @Test
    void setupFieldConfigurationForInstitution_shouldThrowErrorProcessingExpansionException_whenResponseIsInvalid() throws NotSiamoisThesaurusException, ErrorProcessingExpansionException {
        when(conceptApi.fetchFieldsBranch(vocabulary)).thenThrow(ErrorProcessingExpansionException.class);
        assertThrows(ErrorProcessingExpansionException.class, () -> service.setupFieldConfigurationForInstitution(userInfo, vocabulary));
    }

    @Test
    void setupFieldConfigurationForActionUnit_shouldCreateConfigLinkedToActionUnit_whenNoneExists() throws NotSiamoisThesaurusException, ErrorProcessingExpansionException {
        ActionUnitDTO actionUnitDTO = actionUnitDTO(42L);
        ActionUnit actionUnit = new ActionUnit();
        actionUnit.setId(42L);

        when(conceptApi.fetchFieldsBranch(vocabulary)).thenReturn(conceptBranchDTO);
        when(fieldService.searchAllFieldCodes()).thenReturn(List.of("SIATEST", "SIATEST2"));
        when(conceptService.saveOrGetConceptFromFullDTO(any(Vocabulary.class), any(FullInfoDTO.class), eq(null)))
                .thenAnswer(i -> {
                    Concept concept = new Concept();
                    concept.setVocabulary(i.getArgument(0));
                    concept.setExternalId(((FullInfoDTO) i.getArgument(1)).getIdentifier()[0].getValue());
                    return concept;
                });
        when(conceptFieldConfigRepository.findOneByFieldCodeAndActionUnitId(anyString(), eq(42L)))
                .thenReturn(Optional.empty());
        when(actionUnitMapper.invertConvert(actionUnitDTO)).thenReturn(actionUnit);
        when(conceptFieldConfigRepository.save(any(ConceptFieldConfig.class))).thenAnswer(i -> i.getArgument(0));

        Optional<FeedbackFieldConfig> result = service.setupFieldConfigurationForActionUnit(actionUnitDTO, vocabulary);

        assertThat(result).isEmpty();

        ArgumentCaptor<ConceptFieldConfig> captor = ArgumentCaptor.forClass(ConceptFieldConfig.class);
        verify(conceptFieldConfigRepository, times(2)).save(captor.capture());
        assertThat(captor.getAllValues())
                .allSatisfy(saved -> assertThat(saved.getActionUnit()).isEqualTo(actionUnit))
                .extracting(ConceptFieldConfig::getFieldCode)
                .containsExactlyInAnyOrder("SIATEST", "SIATEST2");
        verify(conceptFieldConfigRepository, never()).findOneByFieldCodeForInstitution(anyLong(), anyString());
        verify(conceptService, times(2)).saveAllSubConceptOfIfUpdated(any(ConceptFieldConfig.class), any(ProgressWrapper.class));
    }

    @Test
    void setupFieldConfigurationForActionUnit_shouldUpdateExistingConfig_whenAlreadyExists() throws NotSiamoisThesaurusException, ErrorProcessingExpansionException {
        ActionUnitDTO actionUnitDTO = actionUnitDTO(42L);

        ConceptFieldConfig existing = new ConceptFieldConfig();
        existing.setFieldCode("SIATEST");

        Concept savedConcept = new Concept();
        savedConcept.setVocabulary(vocabulary);
        savedConcept.setExternalId("12");

        when(conceptApi.fetchFieldsBranch(vocabulary)).thenReturn(conceptBranchDTO);
        when(fieldService.searchAllFieldCodes()).thenReturn(List.of("SIATEST"));
        when(conceptService.saveOrGetConceptFromFullDTO(any(Vocabulary.class), any(FullInfoDTO.class), eq(null)))
                .thenReturn(savedConcept);
        when(conceptFieldConfigRepository.findOneByFieldCodeAndActionUnitId("SIATEST", 42L))
                .thenReturn(Optional.of(existing));
        when(conceptFieldConfigRepository.save(any(ConceptFieldConfig.class))).thenAnswer(i -> i.getArgument(0));

        Optional<FeedbackFieldConfig> result = service.setupFieldConfigurationForActionUnit(actionUnitDTO, vocabulary, new ProgressWrapper());

        assertThat(result).isEmpty();
        assertThat(existing.getConcept()).isEqualTo(savedConcept);
        // the existing config is updated in place, no new one is created
        verify(conceptFieldConfigRepository).save(existing);
        verifyNoInteractions(actionUnitMapper);
        verify(conceptService).saveAllSubConceptOfIfUpdated(eq(existing), any(ProgressWrapper.class));
    }

    @Test
    void setupFieldConfigurationForActionUnit_shouldReturnFeedback_whenMissingFieldCodes() throws NotSiamoisThesaurusException, ErrorProcessingExpansionException {
        ActionUnitDTO actionUnitDTO = actionUnitDTO(42L);

        when(conceptApi.fetchFieldsBranch(vocabulary)).thenReturn(conceptBranchDTO);
        when(fieldService.searchAllFieldCodes()).thenReturn(List.of("SIATEST", "SIATEST2", "SIATEST3"));
        when(conceptService.saveOrGetConceptFromFullDTO(any(Vocabulary.class), any(FullInfoDTO.class), eq(null)))
                .thenReturn(new Concept());
        when(conceptFieldConfigRepository.findOneByFieldCodeAndActionUnitId(anyString(), eq(42L)))
                .thenReturn(Optional.of(new ConceptFieldConfig()));

        Optional<FeedbackFieldConfig> result = service.setupFieldConfigurationForActionUnit(actionUnitDTO, vocabulary);

        assertThat(result).isPresent();
        assertThat(result.get().missingFieldCode()).containsExactly("SIATEST3");
    }

    @Test
    void setupFieldConfigurationForActionUnit_shouldThrowNotSiamoisThesaurusException_whenVocabularyIsNotSiamois() throws NotSiamoisThesaurusException, ErrorProcessingExpansionException {
        ActionUnitDTO actionUnitDTO = actionUnitDTO(42L);
        when(conceptApi.fetchFieldsBranch(vocabulary)).thenThrow(NotSiamoisThesaurusException.class);

        assertThrows(NotSiamoisThesaurusException.class,
                () -> service.setupFieldConfigurationForActionUnit(actionUnitDTO, vocabulary));
    }

    private ActionUnitDTO actionUnitDTO(Long id) {
        ActionUnitDTO actionUnitDTO = new ActionUnitDTO();
        actionUnitDTO.setId(id);
        actionUnitDTO.setCreatedByInstitution(userInfo.getInstitution());
        return actionUnitDTO;
    }

    @Test
    void findVocabularyUrlOfInstitution_shouldReturnString_whenConfigExists() {
        Concept c = new Concept();
        c.setVocabulary(vocabulary);
        c.setExternalId("12");
        when(conceptRepository.findTopTermConfigForFieldCodeOfInstitution(1L, SpatialUnit.TYPE_FIELD_CODE))
                .thenReturn(Optional.of(c));
        Optional<String> result = service.findVocabularyUrlOfInstitutionId(1L);

        assertThat(result).isPresent()
                .get()
                .isEqualTo("http://exemple.org/?idt=th2");

    }

    @Test
    void findVocabularyUrlOfInstitution_shouldReturnEmpty_whenConfigDoesNotExist() {
        when(conceptRepository.findTopTermConfigForFieldCodeOfInstitution(1L, SpatialUnit.TYPE_FIELD_CODE))
                .thenReturn(Optional.empty());
        Optional<String> result = service.findVocabularyUrlOfInstitutionId(1L);

        assertThat(result).isEmpty();
    }

    @Test
    void findVocabularyUrlOfActionUnit_shouldReturnString_whenConfigExists() {
        Concept c = new Concept();
        c.setVocabulary(vocabulary);
        c.setExternalId("12");
        ConceptFieldConfig cfc = new ConceptFieldConfig();
        cfc.setConcept(c);
        when(conceptFieldConfigRepository.findOneByFieldCodeAndActionUnitId(SpatialUnit.TYPE_FIELD_CODE, 42L))
                .thenReturn(Optional.of(cfc));

        Optional<String> result = service.findVocabularyUrlOfActionUnitId(42L);

        assertThat(result).isPresent()
                .get()
                .isEqualTo("http://exemple.org/?idt=th2");
    }

    @Test
    void findVocabularyUrlOfActionUnit_shouldReturnEmpty_whenConfigDoesNotExist() {
        when(conceptFieldConfigRepository.findOneByFieldCodeAndActionUnitId(SpatialUnit.TYPE_FIELD_CODE, 42L))
                .thenReturn(Optional.empty());

        Optional<String> result = service.findVocabularyUrlOfActionUnitId(42L);

        assertThat(result).isEmpty();
    }

    @Test
    void findParentConceptForFieldcode_shouldReturnConcept_whenConfigExists() throws NoConfigForFieldException {
        ConceptFieldConfig cfc = new ConceptFieldConfig();
        Concept concept = new Concept();
        concept.setVocabulary(vocabulary);
        concept.setExternalId("12");
        cfc.setConcept(concept);
        cfc.setFieldCode(SpatialUnit.TYPE_FIELD_CODE);

        when(conceptFieldConfigRepository.findOneByFieldCodeForInstitution(userInfo.getInstitution().getId(), SpatialUnit.TYPE_FIELD_CODE))
                .thenReturn(Optional.of(cfc));

        Concept result = service.findParentConceptForFieldcode(userInfo, SpatialUnit.TYPE_FIELD_CODE);

        assertThat(result)
                .isNotNull()
                .isEqualTo(concept);
    }

    @Test
    void findParentConceptForFieldcode_shouldThrowNoConfigException_whenConfigDoesNotExist() {
        assertThrows(NoConfigForFieldException.class, () -> service.findParentConceptForFieldcode(userInfo, SpatialUnit.TYPE_FIELD_CODE));
    }

    @Test
    void getUrlOfConcept_shouldReturnUrl_whenConceptIsValid() {
        Concept concept = new Concept();
        concept.setVocabulary(vocabulary);
        concept.setExternalId("12345");

        String result = service.getUrlOfConcept(concept);

        assertThat(result).isEqualTo("http://exemple.org/?idc=12345&idt=th2");
    }

    @Test
    void getUrlForFieldCode_shouldReturnUrl_whenConfigExists() {
        ConceptFieldConfig cfc = new ConceptFieldConfig();
        Concept concept = new Concept();
        concept.setVocabulary(vocabulary);
        concept.setExternalId("12");
        cfc.setConcept(concept);
        cfc.setFieldCode(SpatialUnit.TYPE_FIELD_CODE);

        when(conceptFieldConfigRepository.findOneByFieldCodeForInstitution(userInfo.getInstitution().getId(), SpatialUnit.TYPE_FIELD_CODE))
                .thenReturn(Optional.of(cfc));

        String result = service.getUrlForFieldCode(userInfo, SpatialUnit.TYPE_FIELD_CODE);

        assertThat(result).isEqualTo("http://exemple.org/?idc=12&idt=th2");
    }

    @Test
    void getUrlForFieldCode_shouldReturnNull_whenConfigDoesNotExist() {
        String result = service.getUrlForFieldCode(userInfo, SpatialUnit.TYPE_FIELD_CODE);

        assertThat(result).isNull();
    }

    @Test
    void getUrlOfCollection_shouldReturnUrl_whenCollectionIsValid() {
        ConceptCollection collection = new ConceptCollection();
        collection.setVocabulary(vocabulary);
        collection.setExternalId("g120");

        String result = service.getUrlOfCollection(collection);

        assertThat(result).isEqualTo("http://exemple.org/?idg=g120&idt=th2");
    }

    @Test
    void getUrlForConceptField_shouldReturnBranchUrl_whenFieldHasABranchConfig() {
        CustomFieldSelectOneFromFieldCode field = new CustomFieldSelectOneFromFieldCode();
        field.setFieldCode("SIARU.GEOMORPHO");

        Concept branchTopTerm = new Concept();
        branchTopTerm.setVocabulary(vocabulary);
        branchTopTerm.setExternalId("266341");

        ConceptFieldFormConfig config = new ConceptFieldFormConfig();
        config.setBranchTopTerm(branchTopTerm);

        when(fieldFormConfigRepository.findDefaultByFieldAndActionUnit(field, 42L)).thenReturn(Optional.of(config));

        String result = service.getUrlForConceptField(field, 42L);

        assertThat(result).isEqualTo("http://exemple.org/?idc=266341&idt=th2");
    }

    @Test
    void getUrlForConceptField_shouldReturnCollectionUrl_whenFieldHasACollectionConfig() {
        CustomFieldSelectOneFromFieldCode field = new CustomFieldSelectOneFromFieldCode();
        field.setFieldCode("SIARU.GEOMORPHO");

        ConceptCollection collection = new ConceptCollection();
        collection.setVocabulary(vocabulary);
        collection.setExternalId("g120");

        ConceptFieldFormConfig config = new ConceptFieldFormConfig();
        config.setCollection(collection);

        when(fieldFormConfigRepository.findDefaultByFieldAndActionUnit(field, 42L)).thenReturn(Optional.of(config));

        String result = service.getUrlForConceptField(field, 42L);

        assertThat(result).isEqualTo("http://exemple.org/?idg=g120&idt=th2");
    }

    @Test
    void getUrlForConceptField_shouldFallBackOnFieldCode_whenNoFormConfigExists() {
        CustomFieldSelectOneFromFieldCode field = new CustomFieldSelectOneFromFieldCode();
        field.setFieldCode(SpatialUnit.TYPE_FIELD_CODE);

        ConceptFieldConfig cfc = new ConceptFieldConfig();
        Concept concept = new Concept();
        concept.setVocabulary(vocabulary);
        concept.setExternalId("12");
        cfc.setConcept(concept);
        cfc.setFieldCode(SpatialUnit.TYPE_FIELD_CODE);

        when(fieldFormConfigRepository.findDefaultByFieldAndActionUnit(field, 42L)).thenReturn(Optional.empty());
        when(conceptFieldConfigRepository.findOneByFieldCodeAndActionUnitId(SpatialUnit.TYPE_FIELD_CODE, 42L))
                .thenReturn(Optional.of(cfc));

        String result = service.getUrlForConceptField(field, 42L);

        assertThat(result).isEqualTo("http://exemple.org/?idc=12&idt=th2");
    }

    @Test
    void getUrlForConceptField_shouldFallBackOnFieldCode_whenTheFormConfigIsNotValid() {
        // an empty ConceptFieldFormConfig (neither branch nor collection) must be treated the same as
        // no configuration at all, not surfaced as a branch/collection URL
        CustomFieldSelectOneFromFieldCode field = new CustomFieldSelectOneFromFieldCode();
        field.setFieldCode(SpatialUnit.TYPE_FIELD_CODE);

        ConceptFieldConfig cfc = new ConceptFieldConfig();
        Concept concept = new Concept();
        concept.setVocabulary(vocabulary);
        concept.setExternalId("12");
        cfc.setConcept(concept);
        cfc.setFieldCode(SpatialUnit.TYPE_FIELD_CODE);

        when(fieldFormConfigRepository.findDefaultByFieldAndActionUnit(field, 42L)).thenReturn(Optional.of(new ConceptFieldFormConfig()));
        when(conceptFieldConfigRepository.findOneByFieldCodeAndActionUnitId(SpatialUnit.TYPE_FIELD_CODE, 42L))
                .thenReturn(Optional.of(cfc));

        String result = service.getUrlForConceptField(field, 42L);

        assertThat(result).isEqualTo("http://exemple.org/?idc=12&idt=th2");
    }

    @Test
    void getUrlForConceptField_shouldReturnNull_whenNoFormConfigAndFieldIsNotFieldCodeDriven() {
        CustomFieldSelectOne field = new CustomFieldSelectOne();

        when(fieldFormConfigRepository.findDefaultByFieldAndActionUnit(field, 42L)).thenReturn(Optional.empty());

        String result = service.getUrlForConceptField(field, 42L);

        assertThat(result).isNull();
    }

    @Test
    void getUrlForConceptField_shouldReturnNull_whenNoFormConfigAndNoExecutionContextIsBound() {
        CustomFieldSelectOneFromFieldCode field = new CustomFieldSelectOneFromFieldCode();
        field.setFieldCode(SpatialUnit.TYPE_FIELD_CODE);

        when(fieldFormConfigRepository.findDefaultByFieldAndActionUnit(field, 42L)).thenReturn(Optional.empty());
        ExecutionContextHolder.clear();

        String result = service.getUrlForConceptField(field, 42L);

        assertThat(result).isNull();
    }

    @Test
    void getUrlForConceptField_shouldUseValueSpecificConfig_whenItExists() {
        CustomFieldSelectOneFromFieldCode field = new CustomFieldSelectOneFromFieldCode();
        field.setFieldCode("SIARU.GEOMORPHO");

        Concept branchTopTerm = new Concept();
        branchTopTerm.setVocabulary(vocabulary);
        branchTopTerm.setExternalId("266341");

        ConceptFieldFormConfig valueConfig = new ConceptFieldFormConfig();
        valueConfig.setBranchTopTerm(branchTopTerm);

        when(fieldFormConfigRepository.findByFieldAndActionUnitAndValue(field, 42L, 200L)).thenReturn(Optional.of(valueConfig));

        String result = service.getUrlForConceptField(field, 42L, 200L);

        assertThat(result).isEqualTo("http://exemple.org/?idc=266341&idt=th2");
        verify(fieldFormConfigRepository, never()).findDefaultByFieldAndActionUnit(any(), any());
    }

    @Test
    void getUrlForConceptField_shouldFallBackToDefaultConfig_whenValueSpecificConfigDoesNotExist() {
        CustomFieldSelectOneFromFieldCode field = new CustomFieldSelectOneFromFieldCode();
        field.setFieldCode("SIARU.GEOMORPHO");

        ConceptCollection collection = new ConceptCollection();
        collection.setVocabulary(vocabulary);
        collection.setExternalId("g120");

        ConceptFieldFormConfig defaultConfig = new ConceptFieldFormConfig();
        defaultConfig.setCollection(collection);

        when(fieldFormConfigRepository.findByFieldAndActionUnitAndValue(field, 42L, 200L)).thenReturn(Optional.empty());
        when(fieldFormConfigRepository.findDefaultByFieldAndActionUnit(field, 42L)).thenReturn(Optional.of(defaultConfig));

        String result = service.getUrlForConceptField(field, 42L, 200L);

        assertThat(result).isEqualTo("http://exemple.org/?idg=g120&idt=th2");
    }

    @Test
    void getUrlForConceptField_shouldNotQueryValueSpecificConfig_whenValueConceptIdIsNull() {
        CustomFieldSelectOneFromFieldCode field = new CustomFieldSelectOneFromFieldCode();
        field.setFieldCode("SIARU.GEOMORPHO");

        when(fieldFormConfigRepository.findDefaultByFieldAndActionUnit(field, 42L)).thenReturn(Optional.empty());

        service.getUrlForConceptField(field, 42L, null);

        verify(fieldFormConfigRepository, never()).findByFieldAndActionUnitAndValue(any(), any(), any());
    }

    @Test
    void getUrlForConceptField_shouldReturnDifferentUrls_forDifferentValueConcepts_ofTheSameFieldAndProject() {
        // Regression test for the prod bug: a project can configure the same field differently
        // under two value concepts (e.g. Type=Ceramique on a branch, Type=Depot on a collection).
        // Resolving one value's config must never leak into another value's lookup.
        CustomFieldSelectOneFromFieldCode field = new CustomFieldSelectOneFromFieldCode();
        field.setFieldCode("SIARU.GEOMORPHO");

        Concept branchTopTerm = new Concept();
        branchTopTerm.setVocabulary(vocabulary);
        branchTopTerm.setExternalId("266341");
        ConceptFieldFormConfig ceramiqueConfig = new ConceptFieldFormConfig();
        ceramiqueConfig.setBranchTopTerm(branchTopTerm);

        ConceptCollection collection = new ConceptCollection();
        collection.setVocabulary(vocabulary);
        collection.setExternalId("g120");
        ConceptFieldFormConfig depotConfig = new ConceptFieldFormConfig();
        depotConfig.setCollection(collection);

        when(fieldFormConfigRepository.findByFieldAndActionUnitAndValue(field, 42L, 200L)).thenReturn(Optional.of(ceramiqueConfig));
        when(fieldFormConfigRepository.findByFieldAndActionUnitAndValue(field, 42L, 300L)).thenReturn(Optional.of(depotConfig));

        assertThat(service.getUrlForConceptField(field, 42L, 200L)).isEqualTo("http://exemple.org/?idc=266341&idt=th2");
        assertThat(service.getUrlForConceptField(field, 42L, 300L)).isEqualTo("http://exemple.org/?idg=g120&idt=th2");
    }

    @Test
    void findConfigurationForFieldCode_shouldReturnConfig_whenExists() throws NoConfigForFieldException {
        ConceptFieldConfig cfc = new ConceptFieldConfig();
        Concept concept = new Concept();
        concept.setVocabulary(vocabulary);
        concept.setExternalId("12");
        cfc.setConcept(concept);
        cfc.setFieldCode(SpatialUnit.TYPE_FIELD_CODE);

        when(conceptFieldConfigRepository.findOneByFieldCodeForInstitution(userInfo.getInstitution().getId(), SpatialUnit.TYPE_FIELD_CODE))
                .thenReturn(Optional.of(cfc));

        ConceptFieldConfig result = service.findConfigurationForFieldCode(userInfo, SpatialUnit.TYPE_FIELD_CODE);

        assertThat(result)
                .isEqualTo(cfc);
    }

    @Test
    void findConfigurationForFieldCode_shouldThrowNoConfigException_whenConfigDoesNotExist() {
        assertThrows(NoConfigForFieldException.class, () -> service.findConfigurationForFieldCode(userInfo, SpatialUnit.TYPE_FIELD_CODE));
    }

    @Test
    void findConfigurationForFieldCodeWithActionUnit_shouldReturnActionUnitConfig_whenExists() throws NoConfigForFieldException {
        ActionUnitDTO actionUnit = new ActionUnitDTO();
        actionUnit.setId(42L);

        ConceptFieldConfig actionUnitConfig = new ConceptFieldConfig();
        Concept concept = new Concept();
        concept.setVocabulary(vocabulary);
        concept.setExternalId("12");
        actionUnitConfig.setConcept(concept);
        actionUnitConfig.setFieldCode(SpatialUnit.TYPE_FIELD_CODE);

        when(conceptFieldConfigRepository.findOneByFieldCodeAndActionUnitId(SpatialUnit.TYPE_FIELD_CODE, 42L))
                .thenReturn(Optional.of(actionUnitConfig));

        ConceptFieldConfig result = service.findConfigurationForFieldCode(userInfo, SpatialUnit.TYPE_FIELD_CODE, actionUnit);

        assertThat(result).isEqualTo(actionUnitConfig);
        verify(conceptFieldConfigRepository, never()).findOneByFieldCodeForInstitution(anyLong(), anyString());
    }

    @Test
    void findConfigurationForFieldCodeWithActionUnit_shouldFallbackOnInstitutionConfig_whenNoActionUnitConfig() throws NoConfigForFieldException {
        ActionUnitDTO actionUnit = new ActionUnitDTO();
        actionUnit.setId(42L);

        ConceptFieldConfig institutionConfig = new ConceptFieldConfig();
        Concept concept = new Concept();
        concept.setVocabulary(vocabulary);
        concept.setExternalId("12");
        institutionConfig.setConcept(concept);
        institutionConfig.setFieldCode(SpatialUnit.TYPE_FIELD_CODE);

        when(conceptFieldConfigRepository.findOneByFieldCodeAndActionUnitId(SpatialUnit.TYPE_FIELD_CODE, 42L))
                .thenReturn(Optional.empty());
        when(conceptFieldConfigRepository.findOneByFieldCodeForInstitution(userInfo.getInstitution().getId(), SpatialUnit.TYPE_FIELD_CODE))
                .thenReturn(Optional.of(institutionConfig));

        ConceptFieldConfig result = service.findConfigurationForFieldCode(userInfo, SpatialUnit.TYPE_FIELD_CODE, actionUnit);

        assertThat(result).isEqualTo(institutionConfig);
    }

    @Test
    void findConfigurationForFieldCodeWithActionUnit_shouldThrowNoConfigException_whenNoConfigAtAll() {
        ActionUnitDTO actionUnit = new ActionUnitDTO();
        actionUnit.setId(42L);

        when(conceptFieldConfigRepository.findOneByFieldCodeAndActionUnitId(SpatialUnit.TYPE_FIELD_CODE, 42L))
                .thenReturn(Optional.empty());
        when(conceptFieldConfigRepository.findOneByFieldCodeForInstitution(userInfo.getInstitution().getId(), SpatialUnit.TYPE_FIELD_CODE))
                .thenReturn(Optional.empty());

        assertThrows(NoConfigForFieldException.class,
                () -> service.findConfigurationForFieldCode(userInfo, SpatialUnit.TYPE_FIELD_CODE, actionUnit));
    }

    @Test
    void findConfigurationForFieldCodeWithActionUnitId_shouldReturnInstitutionConfig_whenActionUnitIdIsNull() throws NoConfigForFieldException {
        ConceptFieldConfig institutionConfig = new ConceptFieldConfig();
        institutionConfig.setFieldCode(SpatialUnit.TYPE_FIELD_CODE);

        when(conceptFieldConfigRepository.findOneByFieldCodeForInstitution(userInfo.getInstitution().getId(), SpatialUnit.TYPE_FIELD_CODE))
                .thenReturn(Optional.of(institutionConfig));

        ConceptFieldConfig result = service.findConfigurationForFieldCode(userInfo, SpatialUnit.TYPE_FIELD_CODE, (Long) null);

        assertThat(result).isEqualTo(institutionConfig);
        verify(conceptFieldConfigRepository, never()).findOneByFieldCodeAndActionUnitId(anyString(), anyLong());
    }

    @Test
    void findConfigurationForFieldCodeWithActionUnitId_shouldReturnActionUnitConfig_whenExists() throws NoConfigForFieldException {
        ConceptFieldConfig actionUnitConfig = new ConceptFieldConfig();
        actionUnitConfig.setFieldCode(SpatialUnit.TYPE_FIELD_CODE);

        when(conceptFieldConfigRepository.findOneByFieldCodeAndActionUnitId(SpatialUnit.TYPE_FIELD_CODE, 42L))
                .thenReturn(Optional.of(actionUnitConfig));

        ConceptFieldConfig result = service.findConfigurationForFieldCode(userInfo, SpatialUnit.TYPE_FIELD_CODE, 42L);

        assertThat(result).isEqualTo(actionUnitConfig);
        verify(conceptFieldConfigRepository, never()).findOneByFieldCodeForInstitution(anyLong(), anyString());
    }

    @Test
    void findConfigurationForFieldCodeWithActionUnitId_shouldFallbackOnInstitutionConfig_whenNoActionUnitConfig() throws NoConfigForFieldException {
        ConceptFieldConfig institutionConfig = new ConceptFieldConfig();
        institutionConfig.setFieldCode(SpatialUnit.TYPE_FIELD_CODE);

        when(conceptFieldConfigRepository.findOneByFieldCodeAndActionUnitId(SpatialUnit.TYPE_FIELD_CODE, 42L))
                .thenReturn(Optional.empty());
        when(conceptFieldConfigRepository.findOneByFieldCodeForInstitution(userInfo.getInstitution().getId(), SpatialUnit.TYPE_FIELD_CODE))
                .thenReturn(Optional.of(institutionConfig));

        ConceptFieldConfig result = service.findConfigurationForFieldCode(userInfo, SpatialUnit.TYPE_FIELD_CODE, 42L);

        assertThat(result).isEqualTo(institutionConfig);
    }

    @Test
    void fetchAutocomplete_withActionUnitId_shouldUseActionUnitConfig_whenExists() throws NoConfigForFieldException {
        String fieldCode = "TESTFIELD";
        String query = "test query";

        ConceptFieldConfig cfc = new ConceptFieldConfig();
        Concept concept = new Concept();
        concept.setVocabulary(vocabulary);
        concept.setExternalId("12");
        cfc.setConcept(concept);
        cfc.setFieldCode(fieldCode);

        when(conceptFieldConfigRepository.findOneByFieldCodeAndActionUnitId(fieldCode, 42L))
                .thenReturn(Optional.of(cfc));

        List<ConceptAutocompleteDTO> expectedResults = List.of(
                new ConceptAutocompleteDTO(new ConceptDTO(), "Concept 100", "100"));
        when(autocompleteRepository.findMatchingConceptsFor(cfc.getConcept(), "fr", query, 200)).thenReturn(expectedResults);

        List<ConceptAutocompleteDTO> results = service.fetchAutocomplete(userInfo, fieldCode, query, 42L);

        assertThat(results).isEqualTo(expectedResults);
        verify(conceptFieldConfigRepository, never()).findOneByFieldCodeForInstitution(anyLong(), anyString());
    }

    @Test
    void fetchAutocompleteRelated_withActionUnitId_shouldUseActionUnitConfig_whenExists() throws NoConfigForFieldException {
        String fieldCode = "TESTFIELD";
        String query = "test query";

        Concept fieldConcept = new Concept();
        fieldConcept.setVocabulary(vocabulary);
        fieldConcept.setExternalId("field-concept");

        ConceptFieldConfig cfc = new ConceptFieldConfig();
        cfc.setConcept(fieldConcept);
        cfc.setFieldCode(fieldCode);

        when(conceptFieldConfigRepository.findOneByFieldCodeAndActionUnitId(fieldCode, 42L))
                .thenReturn(Optional.of(cfc));

        Concept baseValue = new Concept();
        baseValue.setVocabulary(vocabulary);
        baseValue.setExternalId("base-value");

        List<ConceptAutocompleteDTO> expectedResults = List.of(
                new ConceptAutocompleteDTO(new ConceptDTO(), "Concept 100", "100"));
        when(autocompleteRepository.findMatchingConceptsFromRelatedFor(fieldConcept, baseValue, "fr", query, FieldConfigurationService.LIMIT_RESULTS))
                .thenReturn(expectedResults);

        List<ConceptAutocompleteDTO> results = service.fetchAutocompleteRelated(userInfo, fieldCode, baseValue, query, 42L);

        assertThat(results).isEqualTo(expectedResults);
        verify(conceptFieldConfigRepository, never()).findOneByFieldCodeForInstitution(anyLong(), anyString());
    }

    @Test
    void getUrlForFieldCode_withActionUnitId_shouldReturnUrl_whenActionUnitConfigExists() {
        ConceptFieldConfig cfc = new ConceptFieldConfig();
        Concept concept = new Concept();
        concept.setVocabulary(vocabulary);
        concept.setExternalId("12");
        cfc.setConcept(concept);
        cfc.setFieldCode(SpatialUnit.TYPE_FIELD_CODE);

        when(conceptFieldConfigRepository.findOneByFieldCodeAndActionUnitId(SpatialUnit.TYPE_FIELD_CODE, 42L))
                .thenReturn(Optional.of(cfc));

        String result = service.getUrlForFieldCode(userInfo, SpatialUnit.TYPE_FIELD_CODE, 42L);

        assertThat(result).isEqualTo("http://exemple.org/?idc=12&idt=th2");
    }

    @Test
    void getUrlForFieldCode_withNullActionUnitId_shouldFallBackToInstitutionConfig() {
        ConceptFieldConfig cfc = new ConceptFieldConfig();
        Concept concept = new Concept();
        concept.setVocabulary(vocabulary);
        concept.setExternalId("12");
        cfc.setConcept(concept);
        cfc.setFieldCode(SpatialUnit.TYPE_FIELD_CODE);

        when(conceptFieldConfigRepository.findOneByFieldCodeForInstitution(userInfo.getInstitution().getId(), SpatialUnit.TYPE_FIELD_CODE))
                .thenReturn(Optional.of(cfc));

        String result = service.getUrlForFieldCode(userInfo, SpatialUnit.TYPE_FIELD_CODE, null);

        assertThat(result).isEqualTo("http://exemple.org/?idc=12&idt=th2");
    }

    @Test
    void fetchAutocomplete_shouldThrowNoConfigException_whenConfigDoesNotExist() {
        String fieldCode = "TESTFIELD";
        String query = "test query";

        assertThrows(NoConfigForFieldException.class, () -> service.fetchAutocomplete(userInfo, fieldCode, query));
    }

    @Test
    void fetchAutocomplete_shouldReturnResults_whenConfigExists() throws NoConfigForFieldException {
        String fieldCode = "TESTFIELD";
        String query = "test query";

        ConceptFieldConfig cfc = new ConceptFieldConfig();
        Concept concept = new Concept();
        concept.setVocabulary(vocabulary);
        concept.setExternalId("12");
        cfc.setConcept(concept);
        cfc.setFieldCode(fieldCode);

        when(conceptFieldConfigRepository.findOneByFieldCodeForInstitution(userInfo.getInstitution().getId(), fieldCode))
                .thenReturn(Optional.of(cfc));

        List<ConceptAutocompleteDTO> expectedResults = List.of(
                new ConceptAutocompleteDTO(new ConceptDTO(), "Concept 100", "100"),
                new ConceptAutocompleteDTO(new ConceptDTO(), "Concept 101", "101")
        );
        when(autocompleteRepository.findMatchingConceptsFor(cfc.getConcept(), "fr",query, 200)).thenReturn(expectedResults);

        List<ConceptAutocompleteDTO> results = service.fetchAutocomplete(userInfo, fieldCode, query);

        assertThat(results).isEqualTo(expectedResults);
    }

    @Test
    void fetchAutocompleteRelated_shouldThrowNoConfigException_whenConfigDoesNotExist() {
        Concept baseValue = new Concept();
        baseValue.setVocabulary(vocabulary);
        baseValue.setExternalId("base");

        assertThrows(NoConfigForFieldException.class,
                () -> service.fetchAutocompleteRelated(userInfo, "TESTFIELD", baseValue, "test query"));

        verifyNoInteractions(autocompleteRepository);
    }

    @Test
    void fetchAutocompleteRelated_shouldReturnRelatedConceptsOfBaseValue_restrictedToTheFieldConfig() throws NoConfigForFieldException {
        String fieldCode = "TESTFIELD";
        String query = "test query";

        Concept fieldConcept = new Concept();
        fieldConcept.setVocabulary(vocabulary);
        fieldConcept.setExternalId("field-concept");

        ConceptFieldConfig cfc = new ConceptFieldConfig();
        cfc.setConcept(fieldConcept);
        cfc.setFieldCode(fieldCode);

        when(conceptFieldConfigRepository.findOneByFieldCodeForInstitution(userInfo.getInstitution().getId(), fieldCode))
                .thenReturn(Optional.of(cfc));

        Concept baseValue = new Concept();
        baseValue.setVocabulary(vocabulary);
        baseValue.setExternalId("base-value");

        List<ConceptAutocompleteDTO> expectedResults = List.of(
                new ConceptAutocompleteDTO(new ConceptDTO(), "Concept 100", "100"),
                new ConceptAutocompleteDTO(new ConceptDTO(), "Concept 101", "101")
        );
        when(autocompleteRepository.findMatchingConceptsFromRelatedFor(fieldConcept, baseValue, "fr", query, FieldConfigurationService.LIMIT_RESULTS))
                .thenReturn(expectedResults);

        List<ConceptAutocompleteDTO> results = service.fetchAutocompleteRelated(userInfo, fieldCode, baseValue, query);

        assertThat(results).isEqualTo(expectedResults);
        // the field concept comes from the config, the base value from the caller: the order must not be swapped
        verify(autocompleteRepository).findMatchingConceptsFromRelatedFor(fieldConcept, baseValue, "fr", query, FieldConfigurationService.LIMIT_RESULTS);
    }

    @Test
    void fetchAutocompleteRelated_shouldForwardNullInput_asIs() throws NoConfigForFieldException {
        String fieldCode = "TESTFIELD";

        Concept fieldConcept = new Concept();
        fieldConcept.setVocabulary(vocabulary);
        fieldConcept.setExternalId("field-concept");

        ConceptFieldConfig cfc = new ConceptFieldConfig();
        cfc.setConcept(fieldConcept);
        cfc.setFieldCode(fieldCode);

        when(conceptFieldConfigRepository.findOneByFieldCodeForInstitution(userInfo.getInstitution().getId(), fieldCode))
                .thenReturn(Optional.of(cfc));

        Concept baseValue = new Concept();
        baseValue.setVocabulary(vocabulary);
        baseValue.setExternalId("base-value");

        service.fetchAutocompleteRelated(userInfo, fieldCode, baseValue, null);

        // a null input is forwarded as-is, the SQL function treats it as "no text filter"
        verify(autocompleteRepository).findMatchingConceptsFromRelatedFor(fieldConcept, baseValue, "fr", null, FieldConfigurationService.LIMIT_RESULTS);
    }

    @Test
    void fetchAutocompleteOfField_shouldSearchTheConfiguredBranch_whenFieldIsConfiguredOnATopTerm() throws NoConfigForFieldException {
        CustomFieldSelectOne field = new CustomFieldSelectOne();
        field.setId(7L);

        Concept topTerm = new Concept();
        topTerm.setId(99L);
        topTerm.setVocabulary(vocabulary);

        ConceptFieldFormConfig config = new ConceptFieldFormConfig();
        config.setBranchTopTerm(topTerm);

        when(fieldFormConfigRepository.findDefaultByFieldAndActionUnit(field, 42L)).thenReturn(Optional.of(config));

        List<ConceptAutocompleteDTO> expectedResults = List.of(
                new ConceptAutocompleteDTO(new ConceptDTO(), "Concept 100", "100"));
        when(autocompleteRepository.findMatchingConceptsInBranchOf(topTerm, "fr", "que", FieldConfigurationService.LIMIT_RESULTS))
                .thenReturn(expectedResults);

        List<ConceptAutocompleteDTO> results = service.fetchAutocomplete(field, "que", 42L);

        assertThat(results).isEqualTo(expectedResults);
        verify(autocompleteRepository, never()).findMatchingConceptsInCollection(any(), anyString(), any(), anyInt());
    }

    @Test
    void fetchAutocompleteOfField_shouldSearchTheConfiguredCollection_whenFieldIsConfiguredOnACollection() throws NoConfigForFieldException {
        CustomFieldSelectOne field = new CustomFieldSelectOne();
        field.setId(7L);

        ConceptCollection collection = new ConceptCollection();
        collection.setId(55L);

        ConceptFieldFormConfig config = new ConceptFieldFormConfig();
        config.setCollection(collection);

        when(fieldFormConfigRepository.findDefaultByFieldAndActionUnit(field, 42L)).thenReturn(Optional.of(config));

        List<ConceptAutocompleteDTO> expectedResults = List.of(
                new ConceptAutocompleteDTO(new ConceptDTO(), "Concept 100", "100"));
        when(autocompleteRepository.findMatchingConceptsInCollection(collection, "fr", "que", FieldConfigurationService.LIMIT_RESULTS))
                .thenReturn(expectedResults);

        List<ConceptAutocompleteDTO> results = service.fetchAutocomplete(field, "que", 42L);

        assertThat(results).isEqualTo(expectedResults);
        verify(autocompleteRepository, never()).findMatchingConceptsInBranchOf(any(), anyString(), any(), anyInt());
    }

    @Test
    void fetchAutocompleteOfField_shouldFallBackOnTheFieldCodeConfig_whenFieldHasNoFormConfig() throws NoConfigForFieldException {
        String fieldCode = "TESTFIELD";
        CustomFieldSelectOneFromFieldCode field = new CustomFieldSelectOneFromFieldCode();
        field.setId(7L);
        field.setFieldCode(fieldCode);

        when(fieldFormConfigRepository.findDefaultByFieldAndActionUnit(field, 42L)).thenReturn(Optional.empty());

        ConceptFieldConfig cfc = new ConceptFieldConfig();
        Concept concept = new Concept();
        concept.setVocabulary(vocabulary);
        cfc.setConcept(concept);
        cfc.setFieldCode(fieldCode);
        when(conceptFieldConfigRepository.findOneByFieldCodeAndActionUnitId(fieldCode, 42L)).thenReturn(Optional.of(cfc));

        List<ConceptAutocompleteDTO> expectedResults = List.of(
                new ConceptAutocompleteDTO(new ConceptDTO(), "Concept 100", "100"));
        when(autocompleteRepository.findMatchingConceptsFor(concept, "fr", "que", FieldConfigurationService.LIMIT_RESULTS))
                .thenReturn(expectedResults);

        List<ConceptAutocompleteDTO> results = service.fetchAutocomplete(field, "que", 42L);

        assertThat(results).isEqualTo(expectedResults);
    }

    @Test
    void fetchAutocompleteOfField_shouldThrow_whenFieldHasNoFormConfig_andNoFieldCode() {
        CustomFieldSelectOne field = new CustomFieldSelectOne();
        field.setId(7L);

        when(fieldFormConfigRepository.findDefaultByFieldAndActionUnit(field, 42L)).thenReturn(Optional.empty());

        assertThrows(NoConfigForFieldException.class, () -> service.fetchAutocomplete(field, "que", 42L));
        verifyNoInteractions(autocompleteRepository);
    }

    @Test
    void fetchAutocompleteOfField_shouldThrow_whenNoExecutionContextIsBound() {
        // the current user is read from the execution context, there is no other way to know their language
        ExecutionContextHolder.clear();

        CustomFieldSelectOne field = new CustomFieldSelectOne();
        field.setId(7L);

        assertThrows(IllegalStateException.class, () -> service.fetchAutocomplete(field, "que", 42L));
        verifyNoInteractions(fieldFormConfigRepository, autocompleteRepository);
    }

    @Test
    void fetchAutocompleteOfField_shouldThrow_whenFormConfigHasNeitherBranchNorCollection() {
        CustomFieldSelectOne field = new CustomFieldSelectOne();
        field.setId(7L);

        ConceptFieldFormConfig config = new ConceptFieldFormConfig();

        when(fieldFormConfigRepository.findDefaultByFieldAndActionUnit(field, 42L)).thenReturn(Optional.of(config));

        assertThrows(IllegalStateException.class, () -> service.fetchAutocomplete(field, "que", 42L));
        verifyNoInteractions(autocompleteRepository);
    }

    @Test
    void fetchAutocompleteOfField_shouldUseValueSpecificConfig_whenItExists() throws NoConfigForFieldException {
        CustomFieldSelectOne field = new CustomFieldSelectOne();
        field.setId(7L);

        Concept topTerm = new Concept();
        topTerm.setId(99L);
        topTerm.setVocabulary(vocabulary);

        ConceptFieldFormConfig config = new ConceptFieldFormConfig();
        config.setBranchTopTerm(topTerm);

        when(fieldFormConfigRepository.findByFieldAndActionUnitAndValue(field, 42L, 200L)).thenReturn(Optional.of(config));

        List<ConceptAutocompleteDTO> expectedResults = List.of(
                new ConceptAutocompleteDTO(new ConceptDTO(), "Concept 100", "100"));
        when(autocompleteRepository.findMatchingConceptsInBranchOf(topTerm, "fr", "que", FieldConfigurationService.LIMIT_RESULTS))
                .thenReturn(expectedResults);

        List<ConceptAutocompleteDTO> results = service.fetchAutocomplete(field, "que", 42L, 200L);

        assertThat(results).isEqualTo(expectedResults);
        verify(fieldFormConfigRepository, never()).findDefaultByFieldAndActionUnit(any(), any());
    }

    @Test
    void fetchAutocompleteOfField_shouldFallBackToDefaultConfig_whenValueSpecificConfigDoesNotExist() throws NoConfigForFieldException {
        CustomFieldSelectOne field = new CustomFieldSelectOne();
        field.setId(7L);

        ConceptCollection collection = new ConceptCollection();
        collection.setId(55L);

        ConceptFieldFormConfig config = new ConceptFieldFormConfig();
        config.setCollection(collection);

        when(fieldFormConfigRepository.findByFieldAndActionUnitAndValue(field, 42L, 200L)).thenReturn(Optional.empty());
        when(fieldFormConfigRepository.findDefaultByFieldAndActionUnit(field, 42L)).thenReturn(Optional.of(config));

        List<ConceptAutocompleteDTO> expectedResults = List.of(
                new ConceptAutocompleteDTO(new ConceptDTO(), "Concept 100", "100"));
        when(autocompleteRepository.findMatchingConceptsInCollection(collection, "fr", "que", FieldConfigurationService.LIMIT_RESULTS))
                .thenReturn(expectedResults);

        List<ConceptAutocompleteDTO> results = service.fetchAutocomplete(field, "que", 42L, 200L);

        assertThat(results).isEqualTo(expectedResults);
    }

    @Test
    void fetchAutocompleteOfField_shouldNotQueryValueSpecificConfig_whenValueConceptIdIsNull() {
        CustomFieldSelectOne field = new CustomFieldSelectOne();
        field.setId(7L);

        when(fieldFormConfigRepository.findDefaultByFieldAndActionUnit(field, 42L)).thenReturn(Optional.empty());

        assertThrows(NoConfigForFieldException.class, () -> service.fetchAutocomplete(field, "que", 42L, null));

        verify(fieldFormConfigRepository, never()).findByFieldAndActionUnitAndValue(any(), any(), any());
    }

    @Test
    void fetchAutocompleteOfField_shouldReturnDistinctResults_forDifferentValueConcepts_ofTheSameFieldAndProject() throws NoConfigForFieldException {
        // Same regression scenario as getUrlForConceptField, for the autocomplete lookup path.
        CustomFieldSelectOne field = new CustomFieldSelectOne();
        field.setId(7L);

        Concept topTerm = new Concept();
        topTerm.setId(99L);
        ConceptFieldFormConfig ceramiqueConfig = new ConceptFieldFormConfig();
        ceramiqueConfig.setBranchTopTerm(topTerm);

        ConceptCollection collection = new ConceptCollection();
        collection.setId(55L);
        ConceptFieldFormConfig depotConfig = new ConceptFieldFormConfig();
        depotConfig.setCollection(collection);

        when(fieldFormConfigRepository.findByFieldAndActionUnitAndValue(field, 42L, 200L)).thenReturn(Optional.of(ceramiqueConfig));
        when(fieldFormConfigRepository.findByFieldAndActionUnitAndValue(field, 42L, 300L)).thenReturn(Optional.of(depotConfig));

        List<ConceptAutocompleteDTO> ceramiqueResults = List.of(new ConceptAutocompleteDTO(new ConceptDTO(), "Ceramique concept", "1"));
        List<ConceptAutocompleteDTO> depotResults = List.of(new ConceptAutocompleteDTO(new ConceptDTO(), "Depot concept", "2"));
        when(autocompleteRepository.findMatchingConceptsInBranchOf(topTerm, "fr", "que", FieldConfigurationService.LIMIT_RESULTS))
                .thenReturn(ceramiqueResults);
        when(autocompleteRepository.findMatchingConceptsInCollection(collection, "fr", "que", FieldConfigurationService.LIMIT_RESULTS))
                .thenReturn(depotResults);

        assertThat(service.fetchAutocomplete(field, "que", 42L, 200L)).isEqualTo(ceramiqueResults);
        assertThat(service.fetchAutocomplete(field, "que", 42L, 300L)).isEqualTo(depotResults);
    }

    @Test
    void resultLimit_shoudReturnCurrentResultLimit() {
        assertThat(service.resultLimit()).isEqualTo(FieldConfigurationService.LIMIT_RESULTS);
    }

    @Test
    void fetchAllConfiguredVocabularies_returnsMapPerFieldCode() {
        String fieldCode = "TESTFIELD";
        when(conceptFieldConfigRepository.findDistinctFieldCodesForInstitution(
                userInfo.getInstitution().getId()))
                .thenReturn(List.of(fieldCode));

        ConceptFieldConfig cfc = new ConceptFieldConfig();
        Concept concept = new Concept();
        concept.setVocabulary(vocabulary);
        cfc.setConcept(concept);
        cfc.setFieldCode(fieldCode);
        when(conceptFieldConfigRepository.findOneByFieldCodeForInstitution(userInfo.getInstitution().getId(), fieldCode))
                .thenReturn(Optional.of(cfc));

        List<ConceptAutocompleteDTO> expected = List.of(
                new ConceptAutocompleteDTO(new ConceptDTO(), "A", "fr"));
        when(autocompleteRepository.findMatchingConceptsFor(cfc.getConcept(), "fr", null, FieldConfigurationService.LIMIT_RESULTS))
                .thenReturn(expected);

        var result = service.fetchAllConfiguredVocabularies(userInfo);

        assertThat(result).containsEntry(fieldCode, expected);
    }


    // --- vocabulary mode and concept states -------------------------------------------------------

    private static ConceptAutocompleteDTO candidate(long conceptId, String label) {
        ConceptDTO concept = new ConceptDTO();
        concept.setId(conceptId);
        return new ConceptAutocompleteDTO(concept, label, "fr");
    }

    private static ConceptFieldState state(Concept concept, ConceptState state) {
        ConceptFieldState s = new ConceptFieldState();
        s.setConcept(concept);
        s.setState(state);
        return s;
    }

    private static Concept conceptWithId(long id) {
        Concept c = new Concept();
        c.setId(id);
        return c;
    }

    private ConceptFieldFormConfig branchConfig(CustomFieldSelectOne field, VocabularyMode mode, FormConfig formConfig, Concept topTerm) {
        ConceptFieldFormConfig config = new ConceptFieldFormConfig();
        config.setBranchTopTerm(topTerm);
        config.setVocabularyMode(mode);
        config.setFormConfig(formConfig);
        lenient().when(fieldFormConfigRepository.findDefaultByFieldAndActionUnit(field, 42L)).thenReturn(Optional.of(config));
        return config;
    }

    @Test
    void fetchAutocompleteOfField_shouldDropDisabledConcepts_andReadThatManyMore_whenListFollowsItsSource() throws NoConfigForFieldException {
        CustomFieldSelectOne field = new CustomFieldSelectOne();
        field.setId(7L);
        Concept topTerm = conceptWithId(99L);
        FormConfig formConfig = new FormConfig();
        branchConfig(field, VocabularyMode.FOLLOW, formConfig, topTerm);

        when(conceptFieldStateRepository.findAllByFieldAndFormConfig(field, formConfig))
                .thenReturn(List.of(state(conceptWithId(101L), ConceptState.DISABLED), state(conceptWithId(102L), ConceptState.DISABLED)));
        when(autocompleteRepository.findMatchingConceptsInBranchOf(topTerm, "fr", "que", FieldConfigurationService.LIMIT_RESULTS + 2))
                .thenReturn(List.of(candidate(100L, "A"), candidate(101L, "B"), candidate(102L, "C"), candidate(103L, "D")));

        List<ConceptAutocompleteDTO> results = service.fetchAutocomplete(field, "que", 42L);

        assertThat(results).extracting(r -> r.getConceptLabelToDisplay().getConcept().getId()).containsExactly(100L, 103L);
    }

    @Test
    void fetchAutocompleteOfField_shouldOfferNewConcepts_whenListFollowsItsSource() throws NoConfigForFieldException {
        CustomFieldSelectOne field = new CustomFieldSelectOne();
        field.setId(7L);
        Concept topTerm = conceptWithId(99L);
        FormConfig formConfig = new FormConfig();
        branchConfig(field, VocabularyMode.FOLLOW, formConfig, topTerm);

        // a concept the field has no state for is offered: it follows the thesaurus
        when(conceptFieldStateRepository.findAllByFieldAndFormConfig(field, formConfig))
                .thenReturn(List.of(state(conceptWithId(101L), ConceptState.DISABLED)));
        when(autocompleteRepository.findMatchingConceptsInBranchOf(topTerm, "fr", "que", FieldConfigurationService.LIMIT_RESULTS + 1))
                .thenReturn(List.of(candidate(100L, "A"), candidate(104L, "new in the thesaurus")));

        List<ConceptAutocompleteDTO> results = service.fetchAutocomplete(field, "que", 42L);

        assertThat(results).extracting(r -> r.getConceptLabelToDisplay().getConcept().getId()).containsExactly(100L, 104L);
    }

    @Test
    void fetchAutocompleteOfField_shouldKeepOnlyEnabledConcepts_whenListIsFrozen() throws NoConfigForFieldException {
        CustomFieldSelectOne field = new CustomFieldSelectOne();
        field.setId(7L);
        ConceptCollection collection = new ConceptCollection();
        collection.setId(55L);
        FormConfig formConfig = new FormConfig();
        ConceptFieldFormConfig config = new ConceptFieldFormConfig();
        config.setCollection(collection);
        config.setVocabularyMode(VocabularyMode.FROZEN);
        config.setFormConfig(formConfig);
        when(fieldFormConfigRepository.findDefaultByFieldAndActionUnit(field, 42L)).thenReturn(Optional.of(config));

        when(conceptFieldStateRepository.findAllByFieldAndFormConfig(field, formConfig)).thenReturn(List.of(
                state(conceptWithId(100L), ConceptState.ENABLED),
                state(conceptWithId(101L), ConceptState.DISABLED),
                state(conceptWithId(102L), ConceptState.PENDING)));
        when(autocompleteRepository.findMatchingConceptsInCollection(collection, "fr", "que", FieldConfigurationService.FROZEN_SCAN_LIMIT))
                .thenReturn(List.of(candidate(100L, "A"), candidate(101L, "B"), candidate(102L, "C"), candidate(103L, "no state: not frozen in")));

        List<ConceptAutocompleteDTO> results = service.fetchAutocomplete(field, "que", 42L);

        assertThat(results).extracting(r -> r.getConceptLabelToDisplay().getConcept().getId()).containsExactly(100L);
    }

    @Test
    void describeVocabulary_shouldDescribeABranchWithItsDisabledConcepts() {
        CustomFieldSelectOne field = new CustomFieldSelectOne();
        field.setId(7L);
        Concept topTerm = conceptWithId(99L);
        FormConfig formConfig = new FormConfig();
        branchConfig(field, VocabularyMode.FOLLOW, formConfig, topTerm);
        when(conceptFieldStateRepository.findAllByFieldAndFormConfig(field, formConfig))
                .thenReturn(List.of(state(conceptWithId(101L), ConceptState.DISABLED)));

        FieldVocabularyConfig result = service.describeVocabulary(field, 42L, null);

        assertThat(result).isEqualTo(new FieldVocabularyConfig(FieldVocabularyConfig.Kind.BRANCH, null, 99L, null,
                VocabularyMode.FOLLOW, Map.of(101L, ConceptState.DISABLED)));
    }

    @Test
    void describeVocabulary_shouldDescribeACollection() {
        CustomFieldSelectOne field = new CustomFieldSelectOne();
        field.setId(7L);
        ConceptCollection collection = new ConceptCollection();
        collection.setId(55L);
        ConceptFieldFormConfig config = new ConceptFieldFormConfig();
        config.setCollection(collection);
        when(fieldFormConfigRepository.findDefaultByFieldAndActionUnit(field, 42L)).thenReturn(Optional.of(config));

        FieldVocabularyConfig result = service.describeVocabulary(field, 42L, null);

        assertThat(result).isEqualTo(new FieldVocabularyConfig(FieldVocabularyConfig.Kind.COLLECTION, null, null, 55L,
                VocabularyMode.FOLLOW, Map.of()));
    }

    @Test
    void describeVocabulary_shouldFallBackOnTheFieldCode_whenFieldHasNoRestriction() {
        CustomFieldSelectOneFromFieldCode field = new CustomFieldSelectOneFromFieldCode();
        field.setId(7L);
        field.setFieldCode("SIARU.TYPE");
        when(fieldFormConfigRepository.findDefaultByFieldAndActionUnit(field, 42L)).thenReturn(Optional.empty());

        FieldVocabularyConfig result = service.describeVocabulary(field, 42L, null);

        assertThat(result).isEqualTo(new FieldVocabularyConfig(FieldVocabularyConfig.Kind.FIELD_CODE, "SIARU.TYPE", null, null,
                VocabularyMode.FOLLOW, Map.of()));
        verifyNoInteractions(conceptFieldStateRepository);
    }

    @Test
    void describeVocabulary_shouldReturnNull_whenFieldHasNeitherRestrictionNorFieldCode() {
        CustomFieldSelectOne field = new CustomFieldSelectOne();
        field.setId(7L);
        when(fieldFormConfigRepository.findDefaultByFieldAndActionUnit(field, 42L)).thenReturn(Optional.empty());

        assertThat(service.describeVocabulary(field, 42L, null)).isNull();
    }
}
