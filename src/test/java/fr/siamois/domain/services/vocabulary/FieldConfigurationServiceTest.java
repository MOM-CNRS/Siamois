package fr.siamois.domain.services.vocabulary;

import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.exceptions.ErrorProcessingExpansionException;
import fr.siamois.domain.models.exceptions.api.NotSiamoisThesaurusException;
import fr.siamois.domain.models.institution.Institution;
import fr.siamois.domain.models.settings.ConceptFieldConfig;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.models.vocabulary.FeedbackFieldConfig;
import fr.siamois.domain.models.vocabulary.Vocabulary;
import fr.siamois.domain.models.vocabulary.VocabularyType;
import fr.siamois.infrastructure.api.ConceptApi;
import fr.siamois.infrastructure.api.dto.ConceptBranchDTO;
import fr.siamois.infrastructure.api.dto.FullInfoDTO;
import fr.siamois.infrastructure.database.repositories.vocabulary.ConceptFieldConfigRepository;
import fr.siamois.infrastructure.database.repositories.vocabulary.ConceptRepository;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
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
        Assertions.assertThrows(NotSiamoisThesaurusException.class, () -> service.setupFieldConfigurationForInstitution(userInfo, vocabulary));
    }

    @Test
    void setupFieldConfigurationForInstitution_shouldThrowErrorProcessingExpansionException_whenResponseIsInvalid() throws NotSiamoisThesaurusException, ErrorProcessingExpansionException {
        when(conceptApi.fetchFieldsBranch(vocabulary)).thenThrow(ErrorProcessingExpansionException.class);
        Assertions.assertThrows(ErrorProcessingExpansionException.class, () -> service.setupFieldConfigurationForInstitution(userInfo, vocabulary));
    }

}