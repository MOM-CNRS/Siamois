package fr.siamois.ui.api.openapi.v1.controller;

import fr.siamois.domain.services.document.DocumentLinkKind;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.dto.entity.RecordingUnitDTO;
import fr.siamois.ui.api.handler.RestExceptionHandler;
import fr.siamois.ui.api.openapi.v1.controller.recordingunit.RecordingUnitDocumentsControllerApi;
import fr.siamois.ui.api.openapi.v1.generic.response.ListMeta;
import fr.siamois.ui.api.openapi.v1.resource.document.DocumentResource;
import fr.siamois.ui.api.openapi.v1.response.document.DocumentListResponse;
import fr.siamois.ui.api.openapi.v1.service.DocumentLinksOpenApiService;
import fr.siamois.ui.api.openapi.v1.service.DocumentWriteOpenApiService;
import fr.siamois.ui.api.openapi.v1.service.ListQueryStubs;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiCaller;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The recording unit's documents: the historical unpaged list for the mobile, the paged one for the Documents tab. */
@ExtendWith(MockitoExtension.class)
class RecordingUnitDocumentsControllerApiTest {

    @Mock
    private ProjectApiService projectApiService;
    @Mock
    private DocumentWriteOpenApiService documentWriteOpenApiService;
    @Mock
    private DocumentLinksOpenApiService documentLinksOpenApiService;

    private MockMvc mockMvc;
    private ProjectApiCaller caller;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new RecordingUnitDocumentsControllerApi(
                        projectApiService, documentWriteOpenApiService, documentLinksOpenApiService, ListQueryStubs.none()))
                .setControllerAdvice(new RestExceptionHandler())
                .build();
        PersonDTO person = new PersonDTO();
        person.setId(3L);
        caller = new ProjectApiCaller(person, Set.of(10L), List.of());
        when(projectApiService.requireCaller()).thenReturn(caller);
    }

    @Test
    void withoutLimit_keepsTheHistoricalUnpagedList() throws Exception {
        DocumentResource resource = new DocumentResource();
        resource.setId("5");
        when(projectApiService.listDocumentsForAccessibleRecordingUnit(caller, "42")).thenReturn(List.of(resource));

        mockMvc.perform(get("/api/v1/recording-units/42/documents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value("5"))
                .andExpect(jsonPath("$.meta").doesNotExist());

        verify(documentLinksOpenApiService, never()).page(any(), org.mockito.ArgumentMatchers.anyLong(), any(), org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt(), any(), any(), any(), any(), any());
    }

    @Test
    void withLimit_isThePagedListOfTheNumericId() throws Exception {
        RecordingUnitDTO ru = new RecordingUnitDTO();
        ru.setId(42L);
        when(projectApiService.requireViewableRecordingUnit(caller, "UE-42")).thenReturn(ru);
        DocumentResource resource = new DocumentResource();
        resource.setId("5");
        when(documentLinksOpenApiService.page(eq(DocumentLinkKind.RECORDING_UNIT), eq(42L), eq(caller), eq(0), eq(10),
                eq("identifier:asc"), isNull(), any(), isNull(), eq("fr")))
                .thenReturn(new DocumentListResponse(List.of(resource), new ListMeta(1L, 10, 0L)));

        mockMvc.perform(get("/api/v1/recording-units/UE-42/documents").param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Total-Count", "1"))
                .andExpect(jsonPath("$.data[0].id").value("5"));
        verify(projectApiService).validatePagedListRequest(0, 10);
    }
}
