package fr.siamois.ui.config;

import fr.siamois.domain.models.auth.Person;
import fr.siamois.infrastructure.database.repositories.person.PersonRepository;
import fr.siamois.ui.config.security.jwt.JwtAuthenticationFilter;
import fr.siamois.ui.config.security.jwt.JwtService;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * Guards {@code WebSecurityConfig#apiV1SecurityFilterChain}'s {@code sessionFixation(none())}.
 * <p>
 * The React panel calls {@code /api/v1} in parallel with the JSF page open. With Spring's default
 * strategy, every JWT-authenticated call was taken for a new login and changed the id of the JSF
 * session whose cookie the browser sent along; parallel calls raced their {@code Set-Cookie} and the
 * browser kept a dead id ("sent back to login on F5"). SonarCloud flags the {@code none()} as a
 * missing session-fixation protection: it is a false positive on this stateless chain, and this test
 * is what stops someone from "fixing" it.
 */
@SpringJUnitWebConfig(ApiV1SessionSafetyTest.Config.class)
class ApiV1SessionSafetyTest {

    private static final String TOKEN = "valid-access-token";

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void anApiCallCarryingAnHttpSessionNeverTouchesIt() throws Exception {
        MockHttpSession jsfSession = new MockHttpSession();
        String idBefore = jsfSession.getId();

        MvcResult result = mockMvc.perform(get("/api/v1/ping")
                        .header("Authorization", "Bearer " + TOKEN)
                        .session(jsfSession))
                .andReturn();

        assertEquals(200, result.getResponse().getStatus());
        assertEquals(idBefore, jsfSession.getId(), "the API call must not change the JSF session id");
        assertTrue(result.getResponse().getHeaders("Set-Cookie").isEmpty(),
                "the API call must not answer with a session cookie");
    }

    @Test
    void anApiCallWithoutAnySessionCreatesNone() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/ping").header("Authorization", "Bearer " + TOKEN))
                .andReturn();

        assertEquals(200, result.getResponse().getStatus());
        assertEquals(null, result.getRequest().getSession(false), "a stateless chain must not open a session");
        assertTrue(result.getResponse().getHeaders("Set-Cookie").isEmpty());
    }

    @Configuration
    @EnableWebSecurity
    @EnableWebMvc
    static class Config {

        @Bean
        JwtAuthenticationFilter jwtAuthenticationFilter() {
            JwtService jwtService = mock(JwtService.class);
            Claims claims = mock(Claims.class);
            when(claims.getSubject()).thenReturn("1");
            when(jwtService.parseAndValidateAccessToken(TOKEN)).thenReturn(claims);

            Person person = mock(Person.class);
            when(person.isEnabled()).thenReturn(true);
            when(person.getAuthorities()).thenReturn(List.of());
            PersonRepository personRepository = mock(PersonRepository.class);
            when(personRepository.findById(1L)).thenReturn(Optional.of(person));

            return new JwtAuthenticationFilter(jwtService, personRepository);
        }

        @Bean
        SecurityFilterChain apiV1SecurityFilterChain(HttpSecurity http, JwtAuthenticationFilter jwtFilter) throws Exception {
            return new WebSecurityConfig().apiV1SecurityFilterChain(http, jwtFilter);
        }

        @Bean
        PingController pingController() {
            return new PingController();
        }
    }

    @RestController
    @RequestMapping("/api/v1")
    static class PingController {
        @GetMapping("/ping")
        String ping() {
            return "pong";
        }
    }
}
