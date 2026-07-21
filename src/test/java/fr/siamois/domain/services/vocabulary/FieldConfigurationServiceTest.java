package fr.siamois.domain.services.vocabulary;

import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.models.exceptions.ErrorProcessingExpansionException;
import fr.siamois.domain.models.exceptions.api.NotSiamoisThesaurusException;
import fr.siamois.domain.models.exceptions.vocabulary.NoConfigForFieldException;
import fr.siamois.domain.models.misc.ProgressWrapper;
import fr.siamois.domain.models.settings.ConceptFieldConfig;
import fr.siamois.domain.models.spatialunit.SpatialUnit;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.models.vocabulary.FeedbackFieldConfig;
import fr.siamois.domain.models.vocabulary.Vocabulary;
import fr.siamois.domain.models.vocabulary.VocabularyType;
import fr.siamois.dto.entity.ActionUnitDTO;
import fr.siamois.dto.entity.ConceptDTO;
import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.infrastructure.api.ConceptApi;
import fr.siamois.infrastructure.api.dto.ConceptBranchDTO;
import fr.siamois.infrastructure.api.dto.FullInfoDTO;
import fr.siamois.infrastructure.database.repositories.vocabulary.AutocompleteRepository;
import fr.siamois.infrastructure.database.repositories.vocabulary.ConceptFieldConfigRepository;
import fr.siamois.infrastructure.database.repositories.vocabulary.ConceptRepository;
import fr.siamois.infrastructure.database.repositories.vocabulary.dto.ConceptAutocompleteDTO;
import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.mapper.ActionUnitMapper;
import fr.siamois.mapper.InstitutionMapper;
import fr.siamois.mapper.PersonMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
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

        Optional<FeedbackFieldConfig> result = service.setupFieldConfigurationForActionUnit(actionUnitDTO, vocabulary, new ProgressWrapper());

        assertThat(result).isEmpty();
        assertThat(existing.getConcept()).isEqualTo(savedConcept);
        verify(conceptFieldConfigRepository, never()).save(any(ConceptFieldConfig.class));
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
        when(conceptRepository.findTopTermConfigForFieldCodeOfInstitution(1L, SpatialUnit.CATEGORY_FIELD_CODE))
                .thenReturn(Optional.of(c));
        Optional<String> result = service.findVocabularyUrlOfInstitutionId(1L);

        assertThat(result).isPresent()
                .get()
                .isEqualTo("http://exemple.org/?idt=th2");

    }

    @Test
    void findVocabularyUrlOfInstitution_shouldReturnEmpty_whenConfigDoesNotExist() {
        when(conceptRepository.findTopTermConfigForFieldCodeOfInstitution(1L, SpatialUnit.CATEGORY_FIELD_CODE))
                .thenReturn(Optional.empty());
        Optional<String> result = service.findVocabularyUrlOfInstitutionId(1L);

        assertThat(result).isEmpty();
    }

    @Test
    void findParentConceptForFieldcode_shouldReturnConcept_whenConfigExists() throws NoConfigForFieldException {
        ConceptFieldConfig cfc = new ConceptFieldConfig();
        Concept concept = new Concept();
        concept.setVocabulary(vocabulary);
        concept.setExternalId("12");
        cfc.setConcept(concept);
        cfc.setFieldCode(SpatialUnit.CATEGORY_FIELD_CODE);

        when(conceptFieldConfigRepository.findOneByFieldCodeForInstitution(userInfo.getInstitution().getId(), SpatialUnit.CATEGORY_FIELD_CODE))
                .thenReturn(Optional.of(cfc));

        Concept result = service.findParentConceptForFieldcode(userInfo, SpatialUnit.CATEGORY_FIELD_CODE);

        assertThat(result)
                .isNotNull()
                .isEqualTo(concept);
    }

    @Test
    void findParentConceptForFieldcode_shouldThrowNoConfigException_whenConfigDoesNotExist() {
        assertThrows(NoConfigForFieldException.class, () -> service.findParentConceptForFieldcode(userInfo, SpatialUnit.CATEGORY_FIELD_CODE));
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
        cfc.setFieldCode(SpatialUnit.CATEGORY_FIELD_CODE);

        when(conceptFieldConfigRepository.findOneByFieldCodeForInstitution(userInfo.getInstitution().getId(), SpatialUnit.CATEGORY_FIELD_CODE))
                .thenReturn(Optional.of(cfc));

        String result = service.getUrlForFieldCode(userInfo, SpatialUnit.CATEGORY_FIELD_CODE);

        assertThat(result).isEqualTo("http://exemple.org/?idc=12&idt=th2");
    }

    @Test
    void getUrlForFieldCode_shouldReturnNull_whenConfigDoesNotExist() {
        String result = service.getUrlForFieldCode(userInfo, SpatialUnit.CATEGORY_FIELD_CODE);

        assertThat(result).isNull();
    }

    @Test
    void findConfigurationForFieldCode_shouldReturnConfig_whenExists() throws NoConfigForFieldException {
        ConceptFieldConfig cfc = new ConceptFieldConfig();
        Concept concept = new Concept();
        concept.setVocabulary(vocabulary);
        concept.setExternalId("12");
        cfc.setConcept(concept);
        cfc.setFieldCode(SpatialUnit.CATEGORY_FIELD_CODE);

        when(conceptFieldConfigRepository.findOneByFieldCodeForInstitution(userInfo.getInstitution().getId(), SpatialUnit.CATEGORY_FIELD_CODE))
                .thenReturn(Optional.of(cfc));

        ConceptFieldConfig result = service.findConfigurationForFieldCode(userInfo, SpatialUnit.CATEGORY_FIELD_CODE);

        assertThat(result)
                .isEqualTo(cfc);
    }

    @Test
    void findConfigurationForFieldCode_shouldThrowNoConfigException_whenConfigDoesNotExist() {
        assertThrows(NoConfigForFieldException.class, () -> service.findConfigurationForFieldCode(userInfo, SpatialUnit.CATEGORY_FIELD_CODE));
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
        actionUnitConfig.setFieldCode(SpatialUnit.CATEGORY_FIELD_CODE);

        when(conceptFieldConfigRepository.findOneByFieldCodeAndActionUnitId(SpatialUnit.CATEGORY_FIELD_CODE, 42L))
                .thenReturn(Optional.of(actionUnitConfig));

        ConceptFieldConfig result = service.findConfigurationForFieldCode(userInfo, SpatialUnit.CATEGORY_FIELD_CODE, actionUnit);

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
        institutionConfig.setFieldCode(SpatialUnit.CATEGORY_FIELD_CODE);

        when(conceptFieldConfigRepository.findOneByFieldCodeAndActionUnitId(SpatialUnit.CATEGORY_FIELD_CODE, 42L))
                .thenReturn(Optional.empty());
        when(conceptFieldConfigRepository.findOneByFieldCodeForInstitution(userInfo.getInstitution().getId(), SpatialUnit.CATEGORY_FIELD_CODE))
                .thenReturn(Optional.of(institutionConfig));

        ConceptFieldConfig result = service.findConfigurationForFieldCode(userInfo, SpatialUnit.CATEGORY_FIELD_CODE, actionUnit);

        assertThat(result).isEqualTo(institutionConfig);
    }

    @Test
    void findConfigurationForFieldCodeWithActionUnit_shouldThrowNoConfigException_whenNoConfigAtAll() {
        ActionUnitDTO actionUnit = new ActionUnitDTO();
        actionUnit.setId(42L);

        when(conceptFieldConfigRepository.findOneByFieldCodeAndActionUnitId(SpatialUnit.CATEGORY_FIELD_CODE, 42L))
                .thenReturn(Optional.empty());
        when(conceptFieldConfigRepository.findOneByFieldCodeForInstitution(userInfo.getInstitution().getId(), SpatialUnit.CATEGORY_FIELD_CODE))
                .thenReturn(Optional.empty());

        assertThrows(NoConfigForFieldException.class,
                () -> service.findConfigurationForFieldCode(userInfo, SpatialUnit.CATEGORY_FIELD_CODE, actionUnit));
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
    void fetchAutocompleteRelated_shouldReturnRelatedConceptsOfBaseValue() {
        String query = "test query";

        Concept baseValue = new Concept();
        baseValue.setVocabulary(vocabulary);
        baseValue.setExternalId("12");

        List<ConceptAutocompleteDTO> expectedResults = List.of(
                new ConceptAutocompleteDTO(new ConceptDTO(), "Concept 100", "100"),
                new ConceptAutocompleteDTO(new ConceptDTO(), "Concept 101", "101")
        );
        when(autocompleteRepository.findMatchingConceptsFromRelatedFor(baseValue, "fr", query, FieldConfigurationService.LIMIT_RESULTS))
                .thenReturn(expectedResults);

        List<ConceptAutocompleteDTO> results = service.fetchAutocompleteRelated(userInfo, baseValue, query);

        assertThat(results).isEqualTo(expectedResults);
    }

    @Test
    void resultLimit_shoudReturnCurrentResultLimit() {
        assertThat(service.resultLimit()).isEqualTo(FieldConfigurationService.LIMIT_RESULTS);
    }

    @Test
    void fetchAllConfiguredVocabularies_returnsMapPerFieldCode()  {
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
        when(autocompleteRepository.findMatchingConceptsFor(cfc.getConcept(), "fr", null, 200))
                .thenReturn(expected);

        var result = service.fetchAllConfiguredVocabularies(userInfo);

        assertThat(result).containsEntry(fieldCode, expected);
    }

}