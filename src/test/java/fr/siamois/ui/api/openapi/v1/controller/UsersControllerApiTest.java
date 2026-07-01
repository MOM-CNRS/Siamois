package fr.siamois.ui.api.openapi.v1.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.services.InstitutionService;
import fr.siamois.domain.services.person.PersonService;
import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.ui.api.handler.RestExceptionHandler;
import fr.siamois.ui.api.openapi.v1.mapper.PersonOpenApiMapper;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiService;
import fr.siamois.ui.api.openapi.v1.service.UserOpenApiService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Set;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class UsersControllerApiTest {

    @Mock
    private ProjectApiService projectApiService;
    @Mock
    private PersonService personService;
    @Mock
    private InstitutionService institutionService;

    private MockMvc mockMvc;

    private Person person;
    private PersonDTO personDto;
    private InstitutionDTO institutionDto;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        MappingJackson2HttpMessageConverter jsonConverter = new MappingJackson2HttpMessageConverter(objectMapper);

        UserOpenApiService userOpenApiService = new UserOpenApiService(personService, institutionService);
        UsersControllerApi controller = new UsersControllerApi(
                projectApiService, userOpenApiService, new PersonOpenApiMapper());

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new RestExceptionHandler())
                .setMessageConverters(jsonConverter)
                .build();

        person = new Person();
        person.setId(7L);
        personDto = new PersonDTO();
        personDto.setId(7L);

        institutionDto = new InstitutionDTO();
        institutionDto.setId(100L);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void login() {
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                person, null, AuthorityUtils.NO_AUTHORITIES);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @Test
    void listUsers_withoutAuthentication_returns401() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(
                new AnonymousAuthenticationToken("key", "anonymousUser",
                        AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));
        when(projectApiService.requireCaller()).thenThrow(
                new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentification requise"));

        mockMvc.perform(get("/api/v1/users").param("organizationId", "100"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listUsers_success_returnsUsers() throws Exception {
        login();
        when(projectApiService.requireCaller()).thenReturn(
                new fr.siamois.ui.api.openapi.v1.service.ProjectApiCaller(
                        personDto, Set.of(100L), List.of(institutionDto)));
        when(institutionService.findById(100L)).thenReturn(institutionDto);

        PersonDTO alice = new PersonDTO();
        alice.setId(1L);
        alice.setName("Alice");
        alice.setLastname("Martin");
        alice.setEmail("alice@example.org");
        alice.setUsername("alice");
        when(personService.findAllInInstitution(100L, null)).thenReturn(List.of(alice));

        mockMvc.perform(get("/api/v1/users")
                        .param("organizationId", "100")
                        .param("offset", "0")
                        .param("limit", "20"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Total-Count", "1"))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].resourceId").value("1"))
                .andExpect(jsonPath("$.data[0].username").value("alice"))
                .andExpect(jsonPath("$.data[0].name").value("Alice"))
                .andExpect(jsonPath("$.data[0].lastname").value("Martin"))
                .andExpect(jsonPath("$.meta.total").value(1));
    }

    @Test
    void listUsers_organizationOutOfScope_returns403() throws Exception {
        login();
        when(projectApiService.requireCaller()).thenReturn(
                new fr.siamois.ui.api.openapi.v1.service.ProjectApiCaller(
                        personDto, Set.of(10L), List.of(institutionDto)));

        mockMvc.perform(get("/api/v1/users").param("organizationId", "100"))
                .andExpect(status().isForbidden());
    }

    @Test
    void listUsers_unknownOrganization_returns404() throws Exception {
        login();
        when(projectApiService.requireCaller()).thenReturn(
                new fr.siamois.ui.api.openapi.v1.service.ProjectApiCaller(
                        personDto, Set.of(100L), List.of(institutionDto)));
        when(institutionService.findById(100L)).thenReturn(null);

        mockMvc.perform(get("/api/v1/users").param("organizationId", "100"))
                .andExpect(status().isNotFound());
    }

    @Test
    void listUsers_invalidPagination_returns400() throws Exception {
        login();
        doThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Paramètres de pagination invalides"))
                .when(projectApiService).validatePagedListRequest(-1, 20);

        mockMvc.perform(get("/api/v1/users")
                        .param("organizationId", "100")
                        .param("offset", "-1"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listUsers_withSearch_passesFilterToService() throws Exception {
        login();
        when(projectApiService.requireCaller()).thenReturn(
                new fr.siamois.ui.api.openapi.v1.service.ProjectApiCaller(
                        personDto, Set.of(100L), List.of(institutionDto)));
        when(institutionService.findById(100L)).thenReturn(institutionDto);
        when(personService.findAllInInstitution(100L, "mart")).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/users")
                        .param("organizationId", "100")
                        .param("search", "mart"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)));
    }
}
