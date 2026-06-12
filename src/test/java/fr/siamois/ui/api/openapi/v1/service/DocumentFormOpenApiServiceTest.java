package fr.siamois.ui.api.openapi.v1.service;

import fr.siamois.domain.models.document.Document;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.services.InstitutionService;
import fr.siamois.dto.entity.ConceptDTO;
import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.mapper.ConceptMapper;
import fr.siamois.ui.api.openapi.v1.response.document.DocumentFormData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentFormOpenApiServiceTest {

    @Mock
    private InstitutionService institutionService;
    @Mock
    private DocumentContentOpenApiService documentContentOpenApiService;
    @Mock
    private ConceptMapper conceptMapper;

    private DocumentFormOpenApiService service;

    @BeforeEach
    void setUp() {
        service = new DocumentFormOpenApiService(institutionService, documentContentOpenApiService, conceptMapper);
    }

    @Test
    void buildForm_unknownOrganization_throws404() {
        PersonDTO person = new PersonDTO();
        when(institutionService.findById(10L)).thenReturn(null);

        Set<Long> institutionIds = Set.of(10L);
        assertThatThrownBy(() -> service.buildForm(person, 10L, institutionIds, "fr", null))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value()).isEqualTo(404));
    }

    @Test
    void buildForm_creation_returnsFieldsOnly() {
        PersonDTO person = new PersonDTO();
        InstitutionDTO inst = new InstitutionDTO();
        inst.setId(10L);
        when(institutionService.findById(10L)).thenReturn(inst);

        DocumentFormData data = service.buildForm(person, 10L, Set.of(10L), "fr", null);

        assertThat(data.fields()).hasSize(6);
        assertThat(data.fields().get(2).fieldKey()).isEqualTo("nature");
        assertThat(data.fields().get(2).fieldCode()).isEqualTo(Document.NATURE_FIELD_CODE);
        assertThat(data.currentValues()).isNull();
    }

    @Test
    void buildForm_withDocumentId_returnsCurrentValues() {
        PersonDTO person = new PersonDTO();
        InstitutionDTO inst = new InstitutionDTO();
        inst.setId(10L);
        when(institutionService.findById(10L)).thenReturn(inst);

        Document doc = mock(Document.class);
        when(documentContentOpenApiService.requireAccessibleDocument(5L, Set.of(10L))).thenReturn(doc);
        when(doc.getTitle()).thenReturn("T1");
        when(doc.getDescription()).thenReturn("D1");
        when(doc.getFileName()).thenReturn("f.pdf");
        when(doc.getMimeType()).thenReturn("application/pdf");
        Concept nature = mock(Concept.class);
        when(doc.getNature()).thenReturn(nature);
        when(doc.getScale()).thenReturn(null);
        when(doc.getFormat()).thenReturn(null);
        ConceptDTO dto = ConceptDTO.builder().id(99L).externalId("EXT").build();
        when(conceptMapper.convert(nature)).thenReturn(dto);

        DocumentFormData data = service.buildForm(person, 10L, Set.of(10L), "fr", 5L);

        assertThat(data.currentValues()).isNotNull();
        assertThat(data.currentValues().title()).isEqualTo("T1");
        assertThat(data.currentValues().description()).isEqualTo("D1");
        assertThat(data.currentValues().fileName()).isEqualTo("f.pdf");
        assertThat(data.currentValues().mimeType()).isEqualTo("application/pdf");
        assertThat(data.currentValues().nature()).isNotNull();
        assertThat(data.currentValues().scale()).isNull();
        assertThat(data.currentValues().format()).isNull();
        verify(documentContentOpenApiService).requireAccessibleDocument(5L, Set.of(10L));
    }
}
