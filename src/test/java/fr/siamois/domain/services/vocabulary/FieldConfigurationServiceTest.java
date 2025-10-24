package fr.siamois.domain.services.vocabulary;

import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.exceptions.ErrorProcessingExpansionException;
import fr.siamois.domain.models.exceptions.api.NotSiamoisThesaurusException;
import fr.siamois.domain.models.exceptions.vocabulary.NoConfigForFieldException;
import fr.siamois.domain.models.institution.Institution;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.models.vocabulary.GlobalFieldConfig;
import fr.siamois.domain.models.vocabulary.Vocabulary;
import fr.siamois.domain.models.vocabulary.VocabularyType;
import fr.siamois.infrastructure.api.ConceptApi;
import fr.siamois.infrastructure.api.dto.ConceptBranchDTO;
import fr.siamois.infrastructure.api.dto.FullInfoDTO;
import fr.siamois.infrastructure.api.dto.PurlInfoDTO;
import fr.siamois.infrastructure.database.repositories.FieldRepository;
import fr.siamois.infrastructure.database.repositories.vocabulary.ConceptFieldConfigRepository;
import fr.siamois.infrastructure.database.repositories.vocabulary.ConceptRepository;
import org.junit.jupiter.api.BeforeEach;
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

    @Mock private ConceptApi conceptApi;
    @Mock private FieldService fieldService;
    @Mock private FieldRepository fieldRepository;
    @Mock private ConceptRepository conceptRepository;
    @Mock private ConceptService conceptService;
    @Mock private ConceptFieldConfigRepository conceptFieldConfigRepository;

    @InjectMocks
    private FieldConfigurationService service;

    private Vocabulary vocabulary;

    private UserInfo userInfo;

    @BeforeEach
    void beforeEach() {
        VocabularyType type = new VocabularyType();
        type.setId(-1L);
        type.setLabel("Thesaurus");

        vocabulary = new Vocabulary();
        vocabulary.setId(-12L);
        vocabulary.setType(type);
        vocabulary.setBaseUri("http://localhost");
        vocabulary.setExternalVocabularyId("1313");

        userInfo = new UserInfo(new Institution(), new Person(), "fr");
        userInfo.getInstitution().setId(12L);
        userInfo.getUser().setId(12L);
    }

    private FullInfoDTO fullConceptDTO(String id, String code, String prefLabel) {
        FullInfoDTO conceptDTO = new FullInfoDTO();
        PurlInfoDTO identifier = new PurlInfoDTO();
        identifier.setValue(id);
        identifier.setType("string");
        conceptDTO.setIdentifier(new PurlInfoDTO[]{ identifier });
        PurlInfoDTO notation = new PurlInfoDTO();
        notation.setValue("SIAMOIS#" + code);
        notation.setType("string");
        conceptDTO.setNotation(new PurlInfoDTO[]{ notation });
        PurlInfoDTO label = new PurlInfoDTO();
        label.setValue(prefLabel);
        label.setType("string");
        label.setLang("fr");
        conceptDTO.setPrefLabel(new PurlInfoDTO[] { label });

         return conceptDTO;
    }

    private ConceptBranchDTO conceptBranchDTO() {
        ConceptBranchDTO dto = new ConceptBranchDTO();
        dto.addConceptBranchDTO("http://localhost/th223/1212", fullConceptDTO("1212", "SIATEST", "Test concept"));
        return dto;
    }

    @Test
    void setupFieldConfigurationForInstitution_shouldSave_whenNoConfigExist() throws NotSiamoisThesaurusException, ErrorProcessingExpansionException {
        ConceptBranchDTO dto = conceptBranchDTO();

        Concept parentConcept = new Concept();
        parentConcept.setId(-1L);
        parentConcept.setExternalId("1212");
        parentConcept.setVocabulary(vocabulary);
        
        

        when(conceptApi.fetchFieldsBranch(vocabulary)).thenReturn(dto);
        when(fieldService.searchAllFieldCodes()).thenReturn(List.of("SIATEST"));
        when(conceptService.saveOrGetConceptFromFullDTO( any(Vocabulary.class), any(FullInfoDTO.class), any()))
                .thenReturn(parentConcept);
        when(conceptFieldConfigRepository.updateConfigForFieldOfInstitution(anyLong(), anyString(), anyLong())).thenReturn(0);

        Optional<GlobalFieldConfig> result = service.setupFieldConfigurationForInstitution(userInfo, vocabulary);

        assertThat(result).isEmpty();
        verify(conceptFieldConfigRepository, times(1)).saveConceptForFieldOfInstitution(anyLong(), anyString(), anyLong());
    }

    @Test
    void setupFieldConfigurationForInstitution_shouldUpdate_whenConfigExist() throws NotSiamoisThesaurusException, ErrorProcessingExpansionException {
        ConceptBranchDTO dto = conceptBranchDTO();

        Concept parentConcept = new Concept();
        parentConcept.setId(-1L);
        parentConcept.setExternalId("1212");
        parentConcept.setVocabulary(vocabulary);
        
        

        when(conceptApi.fetchFieldsBranch(vocabulary)).thenReturn(dto);
        when(fieldService.searchAllFieldCodes()).thenReturn(List.of("SIATEST"));
        when(conceptService.saveOrGetConceptFromFullDTO( any(Vocabulary.class), any(FullInfoDTO.class), any()))
                .thenReturn(parentConcept);
        when(conceptFieldConfigRepository.updateConfigForFieldOfInstitution(anyLong(), anyString(), anyLong())).thenReturn(1);

        Optional<GlobalFieldConfig> result = service.setupFieldConfigurationForInstitution(userInfo, vocabulary);

        assertThat(result).isEmpty();
        verify(conceptFieldConfigRepository, never()).saveConceptForFieldOfInstitution(anyLong(), anyString(), anyLong());
    }

    @Test
    void setupFieldConfigurationForInstitution_shouldReturnWrongConfig_whenMandatoryCodeIsNotSet() throws NotSiamoisThesaurusException, ErrorProcessingExpansionException {
        ConceptBranchDTO dto = conceptBranchDTO();

        when(fieldService.searchAllFieldCodes()).thenReturn(List.of("SIATEST", "SIAMAND"));
        when(conceptApi.fetchFieldsBranch(vocabulary)).thenReturn(dto);

        Optional<GlobalFieldConfig> result = service.setupFieldConfigurationForInstitution(userInfo, vocabulary);

        assertThat(result).isPresent();
        verify(conceptFieldConfigRepository, never()).updateConfigForFieldOfInstitution(anyLong(), anyString(), anyLong());
        verify(conceptFieldConfigRepository, never()).saveConceptForFieldOfInstitution(anyLong(), anyString(), anyLong());
    }

    @Test
    void setupFieldConfigurationForUser_shouldSave_whenNoConfigExist() throws NotSiamoisThesaurusException, ErrorProcessingExpansionException {
        ConceptBranchDTO dto = conceptBranchDTO();

        Concept parentConcept = new Concept();
        parentConcept.setId(-1L);
        parentConcept.setExternalId("1212");
        parentConcept.setVocabulary(vocabulary);
        
        

        when(conceptApi.fetchFieldsBranch(vocabulary)).thenReturn(dto);
        when(fieldService.searchAllFieldCodes()).thenReturn(List.of("SIATEST"));
        when(conceptService.saveOrGetConceptFromFullDTO( any(Vocabulary.class), any(FullInfoDTO.class), any()))
                .thenReturn(parentConcept);
        when(conceptFieldConfigRepository.updateConfigForFieldOfUser(anyLong(), anyLong(), anyString(), anyLong())).thenReturn(0);

        Optional<GlobalFieldConfig> result = service.setupFieldConfigurationForUser(userInfo, vocabulary);

        assertThat(result).isEmpty();
        verify(conceptFieldConfigRepository, times(1)).saveConceptForFieldOfUser(anyLong(), anyLong(), anyString(), anyLong());
    }

    @Test
    void setupFieldConfigurationForUser_shouldUpdate_whenConfigExist() throws NotSiamoisThesaurusException, ErrorProcessingExpansionException {
        ConceptBranchDTO dto = conceptBranchDTO();

        Concept parentConcept = new Concept();
        parentConcept.setId(-1L);
        parentConcept.setExternalId("1212");
        parentConcept.setVocabulary(vocabulary);
        
        

        when(conceptApi.fetchFieldsBranch(vocabulary)).thenReturn(dto);
        when(fieldService.searchAllFieldCodes()).thenReturn(List.of("SIATEST"));
        when(conceptService.saveOrGetConceptFromFullDTO( any(Vocabulary.class), any(FullInfoDTO.class), any()))
                .thenReturn(parentConcept);
        when(conceptFieldConfigRepository.updateConfigForFieldOfUser(anyLong(), anyLong(),anyString(), anyLong())).thenReturn(1);

        Optional<GlobalFieldConfig> result = service.setupFieldConfigurationForUser(userInfo, vocabulary);

        assertThat(result).isEmpty();
        verify(conceptFieldConfigRepository, never()).saveConceptForFieldOfUser(anyLong(), anyLong(),anyString(), anyLong());
    }

    @Test
    void setupFieldConfigurationForUser_shouldReturnWrongConfig_whenMandatoryCodeIsNotSet() throws NotSiamoisThesaurusException, ErrorProcessingExpansionException {
        ConceptBranchDTO dto = conceptBranchDTO();

        when(fieldService.searchAllFieldCodes()).thenReturn(List.of("SIATEST", "SIAMAND"));
        when(conceptApi.fetchFieldsBranch(vocabulary)).thenReturn(dto);

        Optional<GlobalFieldConfig> result = service.setupFieldConfigurationForUser(userInfo, vocabulary);

        assertThat(result).isPresent();
        verify(conceptFieldConfigRepository, never()).updateConfigForFieldOfUser(anyLong(), anyLong(), anyString(), anyLong());
        verify(conceptFieldConfigRepository, never()).saveConceptForFieldOfUser(anyLong(), anyLong(), anyString(), anyLong());
    }

    @Test
    void findParentConceptForFieldcode_shouldThrow_whenNoConfigSet() {
        assertThrows(NoConfigForFieldException.class, () -> service.findParentConceptForFieldcode(userInfo, "SIATEST"));
    }

    @Test
    void findParentConceptForFieldcode_shouldReturnUserConcept_whenUserConfig() throws NoConfigForFieldException {
        Concept concept = new Concept();
        concept.setId(-1L);
        
        concept.setVocabulary(vocabulary);
        concept.setExternalId("1212");
        

        when(conceptRepository.findTopTermConfigForFieldCodeOfUser(anyLong(), anyLong(), anyString()))
                .thenReturn(Optional.of(concept));

        Concept result = service.findParentConceptForFieldcode(userInfo, "SIATEST");

        assertThat(result).isEqualTo(concept);
    }

    @Test
    void findParentConceptForFieldcode_shouldReturnInstitConcept_whenNoUserConfig() throws NoConfigForFieldException {
        Concept concept = new Concept();
        concept.setId(-1L);
        
        concept.setVocabulary(vocabulary);
        concept.setExternalId("1212");
        

        when(conceptRepository.findTopTermConfigForFieldCodeOfInstitution(anyLong(), anyString()))
                .thenReturn(Optional.of(concept));

        Concept result = service.findParentConceptForFieldcode(userInfo, "SIATEST");

        assertThat(result).isEqualTo(concept);
    }

    @Test
    void getUrlOfConcept_success() {

        Concept concept = new Concept();
        concept.setVocabulary(vocabulary);
        concept.setExternalId("200");

        String url = service.getUrlOfConcept(concept);
        assertThat(url).isEqualTo("http://localhost/?idc=200&idt=1313");
    }

    @Test
    void getUrlForFieldCode_success() {

        Concept concept = new Concept();
        concept.setId(-1L);
        
        concept.setVocabulary(vocabulary);
        concept.setExternalId("1212");
        

        when(conceptRepository.findTopTermConfigForFieldCodeOfInstitution(anyLong(), anyString()))
                .thenReturn(Optional.of(concept));

        String url = service.getUrlForFieldCode(userInfo, "SIATEST");
        assertThat(url).isEqualTo("http://localhost/?idc=1212&idt=1313");
    }

    @Test
    void getUrlForFieldCode_noConfigForField() {
        when(conceptRepository.findTopTermConfigForFieldCodeOfUser(anyLong(), anyLong(), anyString()))
                .thenReturn(Optional.empty());
        when(conceptRepository.findTopTermConfigForFieldCodeOfInstitution(anyLong(), anyString()))
                .thenReturn(Optional.empty());

        String url = service.getUrlForFieldCode(userInfo, "SIATEST");
        assertThat(url).isNull();
    }

    @Test
    void findVocabularyUrl_OfInstitution_shouldReturnEmpty_whenNoConfigForInstitution() {
        Institution institution = new Institution();
        institution.setId(1L);

        when(conceptRepository.findTopTermConfigForFieldCodeOfInstitution(anyLong(), anyString())).thenReturn(Optional.empty());

        Optional<String> result = service.findVocabularyUrlOfInstitution(institution);

        assertThat(result).isEmpty();
    }

    @Test
    void findVocabularyUrl_OfInstitution_shouldReturnString_whenExist() {
        Institution institution = new Institution();
        institution.setId(1L);

        Concept child1 = new Concept();
        child1.setId(1L);
        child1.setVocabulary(vocabulary);

        when(conceptRepository.findTopTermConfigForFieldCodeOfInstitution(anyLong(), anyString())).thenReturn(Optional.of(child1));

        Optional<String> result = service.findVocabularyUrlOfInstitution(institution);

        assertThat(result)
                .isPresent()
                .contains("http://localhost/?idt=1313");
    }

}