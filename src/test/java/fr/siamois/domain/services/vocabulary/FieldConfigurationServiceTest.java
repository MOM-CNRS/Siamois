package fr.siamois.domain.services.vocabulary;

import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.exceptions.ErrorProcessingExpansionException;
import fr.siamois.domain.models.exceptions.api.NotSiamoisThesaurusException;
import fr.siamois.domain.models.exceptions.vocabulary.NoConfigForFieldException;
import fr.siamois.domain.models.institution.Institution;
import fr.siamois.domain.models.settings.ConceptFieldConfig;
import fr.siamois.domain.models.spatialunit.SpatialUnit;
import fr.siamois.domain.models.vocabulary.*;
import fr.siamois.domain.models.vocabulary.label.ConceptLabel;
import fr.siamois.infrastructure.api.ConceptApi;
import fr.siamois.infrastructure.api.dto.ConceptBranchDTO;
import fr.siamois.infrastructure.api.dto.FullInfoDTO;
import fr.siamois.infrastructure.database.repositories.vocabulary.ConceptFieldConfigRepository;
import fr.siamois.infrastructure.database.repositories.vocabulary.ConceptRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
    private ConceptFieldConfigRepository conceptFieldConfigRepository;

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

        userInfo = new UserInfo(new Institution(), new Person(), "fr");
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
        when(conceptFieldConfigRepository.findByFieldCodeForInstitution(eq(userInfo.getInstitution().getId()), anyString())).thenReturn(Optional.empty());
        when(conceptFieldConfigRepository.save(any(ConceptFieldConfig.class))).thenAnswer(i -> i.getArgument(0));

        Optional<FeedbackFieldConfig> result = service.setupFieldConfigurationForInstitution(userInfo, vocabulary);

        assertThat(result).isEmpty();
        verify(conceptFieldConfigRepository, times(2)).save(any(ConceptFieldConfig.class));
        verify(conceptService, times(2)).saveAllSubConceptOfIfUpdated(any(ConceptFieldConfig.class));
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
    void setupFieldConfigurationForUser_shouldReturnFieldConfigIfWrong_whenMissingFieldCodes() throws NotSiamoisThesaurusException, ErrorProcessingExpansionException {
        when(conceptApi.fetchFieldsBranch(vocabulary)).thenReturn(conceptBranchDTO);
        when(fieldService.searchAllFieldCodes()).thenReturn(List.of("SIATEST", "SIATEST2", "SIATEST3"));

        Optional<FeedbackFieldConfig> result = service.setupFieldConfigurationForUser(userInfo, vocabulary);

        assertThat(result).isPresent();
    }

    @Test
    void setupFieldConfigurationForUser_shouldReturnEmptyWhenValid_andConfigDoesNotExist() throws NotSiamoisThesaurusException, ErrorProcessingExpansionException {
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
        when(conceptFieldConfigRepository.findByFieldCodeForUser(eq(userInfo.getUser().getId()), anyString())).thenReturn(Optional.empty());
        when(conceptFieldConfigRepository.save(any(ConceptFieldConfig.class))).thenAnswer(i -> i.getArgument(0));

        Optional<FeedbackFieldConfig> result = service.setupFieldConfigurationForUser(userInfo, vocabulary);

        assertThat(result).isEmpty();
        verify(conceptFieldConfigRepository, times(2)).save(any(ConceptFieldConfig.class));
        verify(conceptService, times(2)).saveAllSubConceptOfIfUpdated(any(ConceptFieldConfig.class));
    }

    @Test
    void setupFieldConfigurationForUser_shouldThrowNotSiamoisThesaurusException_whenSIAAUTOCOMPLETEmissing() throws NotSiamoisThesaurusException, ErrorProcessingExpansionException {
        when(conceptApi.fetchFieldsBranch(vocabulary)).thenThrow(NotSiamoisThesaurusException.class);
        assertThrows(NotSiamoisThesaurusException.class, () -> service.setupFieldConfigurationForUser(userInfo, vocabulary));
    }

    @Test
    void setupFieldConfigurationForUser_shouldThrowErrorProcessingExpansionException_whenResponseIsInvalid() throws NotSiamoisThesaurusException, ErrorProcessingExpansionException {
        when(conceptApi.fetchFieldsBranch(vocabulary)).thenThrow(ErrorProcessingExpansionException.class);
        assertThrows(ErrorProcessingExpansionException.class, () -> service.setupFieldConfigurationForUser(userInfo, vocabulary));
    }

    @Test
    void findVocabularyUrlOfInstitution_shouldReturnString_whenConfigExists() {
        Concept c = new Concept();
        c.setVocabulary(vocabulary);
        c.setExternalId("12");
        when(conceptRepository.findTopTermConfigForFieldCodeOfInstitution(userInfo.getInstitution().getId(), SpatialUnit.CATEGORY_FIELD_CODE))
                .thenReturn(Optional.of(c));
        Optional<String> result = service.findVocabularyUrlOfInstitution(userInfo.getInstitution());

        assertThat(result).isPresent()
                .get()
                .isEqualTo("http://exemple.org/?idt=th2");

    }

    @Test
    void findVocabularyUrlOfInstitution_shouldReturnEmpty_whenConfigDoesNotExist() {
        when(conceptRepository.findTopTermConfigForFieldCodeOfInstitution(userInfo.getInstitution().getId(), SpatialUnit.CATEGORY_FIELD_CODE))
                .thenReturn(Optional.empty());
        Optional<String> result = service.findVocabularyUrlOfInstitution(userInfo.getInstitution());

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

        when(conceptFieldConfigRepository.findByFieldCodeForInstitution(userInfo.getInstitution().getId(), SpatialUnit.CATEGORY_FIELD_CODE))
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

        when(conceptFieldConfigRepository.findByFieldCodeForInstitution(userInfo.getInstitution().getId(), SpatialUnit.CATEGORY_FIELD_CODE))
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

        when(conceptFieldConfigRepository.findByFieldCodeForInstitution(userInfo.getInstitution().getId(), SpatialUnit.CATEGORY_FIELD_CODE))
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
    void fetchAutocomplete_shouldThrowNoConfigException_whenConfigDoesNotExist() {
        String fieldCode = "TESTFIELD";
        String query = "test query";

        assertThrows(NoConfigForFieldException.class, () -> service.fetchAutocomplete(userInfo, fieldCode, query));
    }

}