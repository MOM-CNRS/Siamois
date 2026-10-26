package fr.siamois.ui.api.openapi.v1.service;

import fr.siamois.dto.entity.PersonDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IdentifierSuggestionServiceTest {

    private static final Set<Long> SCOPE = Set.of(10L);

    @Mock
    private RecordingUnitOpenApiService recordingUnitOpenApiService;
    @Mock
    private PhaseOpenApiService phaseOpenApiService;
    @Mock
    private ContainerOpenApiService containerOpenApiService;
    @Mock
    private StudyOpenApiService studyOpenApiService;
    @Mock
    private DocumentOpenApiService documentOpenApiService;
    @Mock
    private FindOpenApiService findOpenApiService;

    private IdentifierSuggestionService service;
    private final PersonDTO person = new PersonDTO();

    @BeforeEach
    void setUp() {
        service = new IdentifierSuggestionService(recordingUnitOpenApiService, phaseOpenApiService,
                containerOpenApiService, studyOpenApiService, documentOpenApiService, findOpenApiService);
    }

    private String suggest(String entityType) {
        return service.suggest(entityType, "7", "187", "2", person, SCOPE, "fr");
    }

    @Test
    void routesEachProjectEntityToItsService_withTheProject() {
        when(recordingUnitOpenApiService.suggestIdentifier("7", "2", person, SCOPE, "fr")).thenReturn("UE-1");
        when(phaseOpenApiService.suggestIdentifier("7", "2", person, SCOPE, "fr")).thenReturn("PH-1");
        when(containerOpenApiService.suggestIdentifier("7", "2", person, SCOPE, "fr")).thenReturn("CT-1");
        when(studyOpenApiService.suggestIdentifier("7", "2", person, SCOPE, "fr")).thenReturn("ET-1");
        when(documentOpenApiService.suggestIdentifier("7", "2", person, SCOPE, "fr")).thenReturn("DOC-1");

        assertThat(suggest("recording-units")).isEqualTo("UE-1");
        assertThat(suggest("phases")).isEqualTo("PH-1");
        assertThat(suggest("containers")).isEqualTo("CT-1");
        assertThat(suggest("studies")).isEqualTo("ET-1");
        assertThat(suggest("documents")).isEqualTo("DOC-1");
        verifyNoInteractions(findOpenApiService);
    }

    @Test
    void routesAFindToItsService_withTheRecordingUnit() {
        when(findOpenApiService.suggestIdentifier("187", "2", person, SCOPE, "fr")).thenReturn("UE1-M1");

        assertThat(suggest("finds")).isEqualTo("UE1-M1");
    }

    @Test
    void anUnknownOrMissingEntityType_is400() {
        for (String entityType : new String[]{"projects", null}) {
            assertThatThrownBy(() -> suggest(entityType))
                    .isInstanceOf(ResponseStatusException.class)
                    .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
        }
    }
}
