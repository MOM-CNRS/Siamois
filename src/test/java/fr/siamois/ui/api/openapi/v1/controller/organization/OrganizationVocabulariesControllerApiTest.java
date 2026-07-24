package fr.siamois.ui.api.openapi.v1.controller.organization;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.ui.api.handler.RestExceptionHandler;
import fr.siamois.ui.api.openapi.v1.resource.vocabulary.VocabularyResource;
import fr.siamois.ui.api.openapi.v1.response.vocabulary.VocabulariesData;
import fr.siamois.ui.api.openapi.v1.response.vocabulary.VocabulariesResponse;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiCaller;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiService;
import fr.siamois.ui.api.openapi.v1.service.VocabularyOpenApiService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class OrganizationVocabulariesControllerApiTest {

    @Mock
    private ProjectApiService projectApiService;
    @Mock
    private VocabularyOpenApiService vocabularyOpenApiService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        MappingJackson2HttpMessageConverter jsonConverter = new MappingJackson2HttpMessageConverter(objectMapper);

        OrganizationVocabulariesControllerApi controller =
                new OrganizationVocabulariesControllerApi(projectApiService, vocabularyOpenApiService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new RestExceptionHandler())
                .setMessageConverters(jsonConverter)
                .build();
    }

    @Test
    void getVocabularies_returns200() throws Exception {
        PersonDTO person = new PersonDTO();
        when(projectApiService.requireCaller()).thenReturn(new ProjectApiCaller(person, Set.of(10L), List.of()));

        VocabularyResource resource = new VocabularyResource();
        resource.setId("3");
        resource.setLabel("SIASU");
        VocabulariesData data = new VocabulariesData(
                "10",
                List.of("SIAAU.TYPE"),
                Map.of(),
                List.of(resource));
        when(vocabularyOpenApiService.listOrganizationVocabularies(any(), eq(10L), eq("fr")))
                .thenReturn(new VocabulariesResponse(data));

        mockMvc.perform(get("/api/v1/organizations/10/vocabularies").header("Accept-Language", "fr"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Total-Count", "0"))
                .andExpect(jsonPath("$.data.organizationId").value("10"))
                .andExpect(jsonPath("$.data.fieldCodes", hasSize(1)))
                .andExpect(jsonPath("$.data.fieldCodes[0]").value("SIAAU.TYPE"))
                .andExpect(jsonPath("$.data.vocabularies", hasSize(1)))
                .andExpect(jsonPath("$.data.vocabularies[0].label").value("SIASU"));

        verify(vocabularyOpenApiService).listOrganizationVocabularies(any(), eq(10L), eq("fr"));
    }

    @Test
    void getVocabularies_withoutAuth_returns401() throws Exception {
        when(projectApiService.requireCaller()).thenThrow(
                new ResponseStatusException(org.springframework.http.HttpStatus.UNAUTHORIZED, "Authentification requise"));

        mockMvc.perform(get("/api/v1/organizations/10/vocabularies"))
                .andExpect(status().isUnauthorized());
    }
}
