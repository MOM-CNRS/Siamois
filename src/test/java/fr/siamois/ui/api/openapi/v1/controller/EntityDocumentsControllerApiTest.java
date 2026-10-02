package fr.siamois.ui.api.openapi.v1.controller;

import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.services.document.DocumentLinkKind;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.ui.api.handler.RestExceptionHandler;
import fr.siamois.ui.api.openapi.v1.generic.response.ListMeta;
import fr.siamois.ui.api.openapi.v1.resource.document.DocumentResource;
import fr.siamois.ui.api.openapi.v1.response.document.DocumentListResponse;
import fr.siamois.ui.api.openapi.v1.service.DocumentLinksOpenApiService;
import fr.siamois.ui.api.openapi.v1.service.ListQueryStubs;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiCaller;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class EntityDocumentsControllerApiTest {

    @Mock
    private ProjectApiService projectApiService;
    @Mock
    private DocumentLinksOpenApiService documentLinksOpenApiService;

    private MockMvc mockMvc;
    private ProjectApiCaller caller;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new EntityDocumentsControllerApi(projectApiService, documentLinksOpenApiService, ListQueryStubs.none()))
                .setControllerAdvice(new RestExceptionHandler())
                .build();
        PersonDTO person = new PersonDTO();
        person.setId(3L);
        caller = new ProjectApiCaller(person, Set.of(10L), List.of());
        lenient().when(projectApiService.requireCaller()).thenReturn(caller);
    }

    @Test
    void getDocuments_ofAPhase_isPagedAndCarriesTheTotal() throws Exception {
        DocumentResource resource = new DocumentResource();
        resource.setId("5");
        when(documentLinksOpenApiService.page(eq(DocumentLinkKind.PHASE), eq(3L), eq(caller), eq(0), eq(10),
                eq("identifier:asc"), isNull(), any(), isNull(), eq("fr")))
                .thenReturn(new DocumentListResponse(List.of(resource), new ListMeta(1L, 10, 0L)));

        mockMvc.perform(get("/api/v1/phases/3/documents"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Total-Count", "1"))
                .andExpect(jsonPath("$.data[0].id").value("5"))
                .andExpect(jsonPath("$.meta.total").value(1));
        verify(projectApiService).validatePagedListRequest(0, 10);
    }

    @Test
    void getDocuments_servesEachOfTheOtherCollections() throws Exception {
        when(documentLinksOpenApiService.page(any(), anyLong(), any(), anyInt(), anyInt(), any(), any(), any(), any(), any()))
                .thenReturn(new DocumentListResponse(List.of(), new ListMeta(0L, 10, 0L)));

        for (String segment : List.of("finds", "containers", "places")) {
            mockMvc.perform(get("/api/v1/" + segment + "/3/documents")).andExpect(status().isOk());
        }
    }

    @Test
    void getDocuments_ofAnInvalidPage_is400() throws Exception {
        doThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Paramètres de pagination invalides"))
                .when(projectApiService).validatePagedListRequest(5, 10);

        mockMvc.perform(get("/api/v1/phases/3/documents").param("offset", "5")).andExpect(status().isBadRequest());
    }

    @Test
    void link_answers204() throws Exception {
        mockMvc.perform(put("/api/v1/recording-units/3/documents/5")).andExpect(status().isNoContent());

        verify(documentLinksOpenApiService).link(DocumentLinkKind.RECORDING_UNIT, 3L, 5L, caller);
    }

    @Test
    void link_withoutTheEditRight_is403() throws Exception {
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "non"))
                .when(documentLinksOpenApiService).link(DocumentLinkKind.FIND, 3L, 5L, caller);

        mockMvc.perform(put("/api/v1/finds/3/documents/5")).andExpect(status().isForbidden());
    }

    @Test
    void unlink_answers204() throws Exception {
        mockMvc.perform(delete("/api/v1/places/3/documents/5")).andExpect(status().isNoContent());

        verify(documentLinksOpenApiService).unlink(DocumentLinkKind.PLACE, 3L, 5L, caller);
    }
}
