package fr.siamois.ui.api.openapi.v1.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import fr.siamois.ui.api.openapi.v1.OpenApiRestExceptionHandler;
import fr.siamois.ui.api.openapi.v1.auth.dto.AuthUserResponse;
import fr.siamois.ui.api.openapi.v1.auth.dto.LoginResponse;
import fr.siamois.ui.api.openapi.v1.auth.dto.OrganizationSummaryResponse;
import fr.siamois.ui.service.auth.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AuthControllerApiTest {

    @Mock
    private AuthService authService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        MappingJackson2HttpMessageConverter json = new MappingJackson2HttpMessageConverter(objectMapper);

        AuthControllerApi controller = new AuthControllerApi(authService);

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new OpenApiRestExceptionHandler())
                .setMessageConverters(json)
                .build();
    }

    @Test
    void login_success_returnsTokenAndUser() throws Exception {
        AuthUserResponse user = new AuthUserResponse(
                1L, "a@b.fr", "user", "Jean", "Dupont", List.of(new OrganizationSummaryResponse(10L, "Org")));
        LoginResponse body = new LoginResponse("access-jwt", 900L, "Bearer", user);
        when(authService.login(eq("a@b.fr"), eq("secret"))).thenReturn(body);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"a@b.fr\",\"password\":\"secret\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access-jwt"))
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.user.email").value("a@b.fr"))
                .andExpect(jsonPath("$.user.organizations[0].name").value("Org"));

        verify(authService).login("a@b.fr", "secret");
    }

    @Test
    void login_badCredentials_returns401Json() throws Exception {
        when(authService.login(anyString(), anyString()))
                .thenThrow(new BadCredentialsException("Invalid credentials"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"x@y.fr\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("unauthorized"))
                .andExpect(jsonPath("$.message").value("Invalid credentials"));
    }

    @Test
    void login_badCredentials_nullMessage_returnsDefaultMessage() throws Exception {
        when(authService.login(anyString(), anyString()))
                .thenThrow(new BadCredentialsException(null));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"a@b.fr\",\"password\":\"x\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid credentials"));
    }

    @Test
    void me_returnsUser() throws Exception {
        AuthUserResponse user = new AuthUserResponse(
                2L, "me@test.fr", "me", "A", "B", List.of());
        when(authService.currentUser()).thenReturn(user);

        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("me@test.fr"))
                .andExpect(jsonPath("$.id").value(2));

        verify(authService).currentUser();
    }

    @Test
    void me_unauthorized_returns401() throws Exception {
        when(authService.currentUser()).thenThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED));

        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized());
    }
}
