package fr.siamois.ui.api.openapi.v1.service;

import fr.siamois.domain.models.vocabulary.Vocabulary;
import fr.siamois.domain.services.InstitutionService;
import fr.siamois.domain.services.vocabulary.FieldConfigurationService;
import fr.siamois.domain.services.vocabulary.VocabularyService;
import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.infrastructure.database.repositories.vocabulary.dto.ConceptAutocompleteDTO;
import fr.siamois.ui.api.openapi.v1.mapper.VocabularyOpenApiMapper;
import fr.siamois.ui.api.openapi.v1.resource.vocabulary.VocabularyResource;
import fr.siamois.ui.api.openapi.v1.response.vocabulary.VocabulariesData;
import fr.siamois.ui.api.openapi.v1.response.vocabulary.VocabulariesResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VocabularyOpenApiServiceTest {

    private static final Set<Long> SCOPE = Set.of(10L);

    @Mock
    private ProjectApiService projectApiService;
    @Mock
    private InstitutionService institutionService;
    @Mock
    private FieldConfigurationService fieldConfigurationService;
    @Mock
    private VocabularyService vocabularyService;
    @Mock
    private VocabularyOpenApiMapper vocabularyOpenApiMapper;

    private VocabularyOpenApiService service;
    private ProjectApiCaller caller;
    private InstitutionDTO institution;

    @BeforeEach
    void setUp() {
        service = new VocabularyOpenApiService(
                projectApiService,
                institutionService,
                fieldConfigurationService,
                vocabularyService,
                vocabularyOpenApiMapper);

        PersonDTO person = new PersonDTO();
        person.setId(1L);
        institution = new InstitutionDTO();
        institution.setId(10L);
        caller = new ProjectApiCaller(person, SCOPE, List.of(institution));
    }

    @Test
    void listOrganizationVocabularies_success() {
        when(institutionService.findById(10L)).thenReturn(institution);

        ConceptAutocompleteDTO item = new ConceptAutocompleteDTO(null, "Label", "fr");
        Map<String, List<ConceptAutocompleteDTO>> vocab = Map.of("SIARU.TYPE", List.of(item));
        when(fieldConfigurationService.fetchAllConfiguredVocabularies(any())).thenReturn(vocab);

        Vocabulary vocabulary = new Vocabulary();
        vocabulary.setId(2L);
        when(vocabularyService.findAllByInstitutionId(10L)).thenReturn(List.of(vocabulary));

        VocabularyResource resource = new VocabularyResource();
        resource.setId("2");
        when(vocabularyOpenApiMapper.toResource(vocabulary, "fr")).thenReturn(resource);

        VocabulariesResponse response = service.listOrganizationVocabularies(caller, 10L, "fr");

        assertThat(response.getData().organizationId()).isEqualTo("10");
        assertThat(response.getData().fieldCodes()).containsExactly("SIARU.TYPE");
        assertThat(response.getData().vocabulariesByFieldCode()).isEqualTo(vocab);
        assertThat(response.getData().vocabularies()).containsExactly(resource);
        verify(projectApiService).assertOrganizationInCallerScope(10L, SCOPE);
        verify(fieldConfigurationService).fetchAllConfiguredVocabularies(any());
        verify(vocabularyService).findAllByInstitutionId(10L);
    }

    @Test
    void listOrganizationVocabularies_unknownOrganization_throws403() {
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "hors périmètre"))
                .when(projectApiService).assertOrganizationInCallerScope(99L, SCOPE);

        assertThatThrownBy(() -> service.listOrganizationVocabularies(caller, 99L, "fr"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value())
                        .isEqualTo(HttpStatus.FORBIDDEN.value()));
    }

    @Test
    void listOrganizationVocabularies_organizationNotFound_throws404() {
        when(institutionService.findById(10L)).thenReturn(null);

        assertThatThrownBy(() -> service.listOrganizationVocabularies(caller, 10L, "fr"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value())
                        .isEqualTo(HttpStatus.NOT_FOUND.value()));
    }

    @Test
    void listVocabulariesForOrganization_unknownOrganization_throws404() {
        when(institutionService.findById(10L)).thenReturn(null);

        var personDto = new PersonDTO();
        assertThatThrownBy(() -> service.listVocabulariesForOrganization(10L, personDto, "fr"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value()).isEqualTo(404));
    }

    @Test
    void listVocabulariesForOrganization_returnsConfiguredVocabularies() {
        when(institutionService.findById(10L)).thenReturn(institution);

        ConceptAutocompleteDTO item = new ConceptAutocompleteDTO(null, "Label", "fr");
        Map<String, List<ConceptAutocompleteDTO>> vocab = Map.of("SIARU.TYPE", List.of(item));
        when(fieldConfigurationService.fetchAllConfiguredVocabularies(any())).thenReturn(vocab);
        when(vocabularyService.findAllByInstitutionId(10L)).thenReturn(List.of());

        VocabulariesData data = service.listVocabulariesForOrganization(10L, new PersonDTO(), "fr");

        assertThat(data.organizationId()).isEqualTo("10");
        assertThat(data.fieldCodes()).containsExactly("SIARU.TYPE");
        assertThat(data.vocabulariesByFieldCode()).isEqualTo(vocab);
        assertThat(data.vocabularies()).isEmpty();
        verify(fieldConfigurationService).fetchAllConfiguredVocabularies(any());
    }
}
