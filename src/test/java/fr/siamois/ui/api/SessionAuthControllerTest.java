package fr.siamois.ui.api;

import fr.siamois.ui.api.openapi.v1.auth.dto.AuthUserResponse;
import fr.siamois.ui.api.openapi.v1.auth.dto.LoginResponse;
import fr.siamois.ui.service.auth.AuthService;
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

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class SessionAuthControllerTest {

    @Mock
    private AuthService authService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        SessionAuthController controller = new SessionAuthController(authService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void sessionToken_delegatesToAuthServiceAndReturnsPayload() throws Exception {
        AuthUserResponse user = new AuthUserResponse(1L, "user", "Jean", "Dupont", List.of());
        LoginResponse body = new LoginResponse("bridged-jwt", 900L, "Bearer", user);
        when(authService.sessionToken()).thenReturn(body);

        mockMvc.perform(post("/api/auth/session-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("bridged-jwt"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"));

        verify(authService).sessionToken();
    }

    @Test
    void sessionToken_noSessionAuthentication_returns401() throws Exception {
        when(authService.sessionToken()).thenThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED));

        mockMvc.perform(post("/api/auth/session-token"))
                .andExpect(status().isUnauthorized());
    }
}
