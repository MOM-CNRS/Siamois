package fr.siamois.ui.api.openapi.v1.service;

import fr.siamois.domain.services.InstitutionService;
import fr.siamois.domain.services.vocabulary.FieldConfigurationService;
import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.infrastructure.database.repositories.vocabulary.dto.ConceptAutocompleteDTO;
import fr.siamois.ui.api.openapi.v1.response.vocabulary.VocabulariesData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VocabularyOpenApiServiceTest {

    @Mock
    private InstitutionService institutionService;
    @Mock
    private FieldConfigurationService fieldConfigurationService;

    private VocabularyOpenApiService service;

    @BeforeEach
    void setUp() {
        service = new VocabularyOpenApiService(institutionService, fieldConfigurationService);
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
        InstitutionDTO inst = new InstitutionDTO();
        inst.setId(10L);
        when(institutionService.findById(10L)).thenReturn(inst);

        ConceptAutocompleteDTO item = new ConceptAutocompleteDTO(null, "Label", "fr");
        Map<String, List<ConceptAutocompleteDTO>> vocab = Map.of("SIARU.X", List.of(item));
        when(fieldConfigurationService.fetchAllConfiguredVocabularies(any())).thenReturn(vocab);

        VocabulariesData data = service.listVocabulariesForOrganization(10L, new PersonDTO(), "fr");

        assertThat(data.vocabulariesByFieldCode()).isEqualTo(vocab);
        verify(fieldConfigurationService).fetchAllConfiguredVocabularies(any());
    }
}
