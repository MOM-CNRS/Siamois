package fr.siamois.ui.config.security.jwt;

import fr.siamois.domain.models.auth.Person;
import fr.siamois.infrastructure.database.repositories.person.PersonRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock
    private JwtService jwtService;
    @Mock
    private PersonRepository personRepository;
    @Mock
    private FilterChain filterChain;

    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        filter = new JwtAuthenticationFilter(jwtService, personRepository);
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void loginPath_passesThroughWithoutParsingJwt() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setServletPath("/api/v1/auth/login");
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilter(req, res, filterChain);

        verify(filterChain).doFilter(req, res);
        verify(jwtService, never()).parseAndValidateAccessToken(anyString());
        assertThat(res.getStatus()).isEqualTo(200);
    }

    @Test
    void logoutPath_requiresJwt_notPublic() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setServletPath("/api/v1/auth/logout");
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilter(req, res, filterChain);

        verify(filterChain, never()).doFilter(req, res);
        assertThat(res.getStatus()).isEqualTo(401);
    }

    @Test
    void loginPath_withTrailingSlash_normalizedAndPassesThrough() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setServletPath("/api/v1/auth/login/");
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilter(req, res, filterChain);

        verify(filterChain).doFilter(req, res);
        verify(jwtService, never()).parseAndValidateAccessToken(anyString());
    }

    @Test
    void refreshPath_requiresJwt_notPublic() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setServletPath("/api/v1/auth/refresh");
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilter(req, res, filterChain);

        verify(filterChain, never()).doFilter(req, res);
        assertThat(res.getStatus()).isEqualTo(401);
    }

    @Test
    void protectedPath_missingAuthorization_returns401_andDoesNotContinueChain() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setServletPath("/api/v1/auth/me");
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilter(req, res, filterChain);

        verify(filterChain, never()).doFilter(req, res);
        assertThat(res.getStatus()).isEqualTo(401);
        assertThat(res.getContentAsString()).contains("Missing or invalid Authorization header");
    }

    @Test
    void protectedPath_basicAuthInsteadOfBearer_returns401() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setServletPath("/api/v1/projects");
        req.addHeader("Authorization", "Basic dXNlcjpwYXNz");
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilter(req, res, filterChain);

        verify(filterChain, never()).doFilter(req, res);
        assertThat(res.getStatus()).isEqualTo(401);
    }

    @Test
    void protectedPath_bearerEmpty_returns401() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setServletPath("/api/v1/projects");
        req.addHeader("Authorization", "Bearer ");
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilter(req, res, filterChain);

        verify(filterChain, never()).doFilter(req, res);
        assertThat(res.getStatus()).isEqualTo(401);
    }

    @Test
    void protectedPath_bearerWhitespaceOnly_returns401() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setServletPath("/api/v1/projects");
        req.addHeader("Authorization", "Bearer    ");
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilter(req, res, filterChain);

        verify(filterChain, never()).doFilter(req, res);
        assertThat(res.getStatus()).isEqualTo(401);
    }

    @Test
    void protectedPath_invalidJwt_returns401() throws Exception {
        when(jwtService.parseAndValidateAccessToken("bad")).thenThrow(new JwtException("bad sig"));

        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setServletPath("/api/v1/projects");
        req.addHeader("Authorization", "Bearer bad");
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilter(req, res, filterChain);

        verify(filterChain, never()).doFilter(req, res);
        assertThat(res.getStatus()).isEqualTo(401);
        assertThat(res.getContentAsString()).contains("Invalid or expired token");
    }

    @Test
    void protectedPath_subjectNotLong_returns401() throws Exception {
        Claims claims = mock(Claims.class);
        when(claims.getSubject()).thenReturn("not-a-number");
        when(jwtService.parseAndValidateAccessToken("tok")).thenReturn(claims);

        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setServletPath("/api/v1/me");
        req.addHeader("Authorization", "Bearer tok");
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilter(req, res, filterChain);

        verify(filterChain, never()).doFilter(req, res);
        assertThat(res.getStatus()).isEqualTo(401);
    }

    @Test
    void protectedPath_unknownPerson_returns401() throws Exception {
        Claims claims = mock(Claims.class);
        when(claims.getSubject()).thenReturn("77");
        when(jwtService.parseAndValidateAccessToken("tok")).thenReturn(claims);
        when(personRepository.findById(77L)).thenReturn(Optional.empty());

        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setServletPath("/api/v1/projects");
        req.addHeader("Authorization", "Bearer tok");
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilter(req, res, filterChain);

        verify(filterChain, never()).doFilter(req, res);
        assertThat(res.getStatus()).isEqualTo(401);
        assertThat(res.getContentAsString()).contains("Unknown or disabled user");
    }

    @Test
    void protectedPath_disabledPerson_returns401() throws Exception {
        Person person = new Person();
        person.setId(5L);
        person.setEnabled(false);

        Claims claims = mock(Claims.class);
        when(claims.getSubject()).thenReturn("5");
        when(jwtService.parseAndValidateAccessToken("tok")).thenReturn(claims);
        when(personRepository.findById(5L)).thenReturn(Optional.of(person));

        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setServletPath("/api/v1/projects");
        req.addHeader("Authorization", "Bearer tok");
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilter(req, res, filterChain);

        verify(filterChain, never()).doFilter(req, res);
        assertThat(res.getStatus()).isEqualTo(401);
    }

    @Test
    void protectedPath_validJwt_setsAuthenticationAndContinuesChain() throws Exception {
        Person person = new Person();
        person.setId(5L);
        person.setEnabled(true);

        Claims claims = mock(Claims.class);
        when(claims.getSubject()).thenReturn("5");
        when(jwtService.parseAndValidateAccessToken("good")).thenReturn(claims);
        when(personRepository.findById(5L)).thenReturn(Optional.of(person));

        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setServletPath("/api/v1/projects");
        req.addHeader("Authorization", "Bearer good");
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilter(req, res, filterChain);

        verify(filterChain).doFilter(req, res);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal()).isSameAs(person);
    }

    @Test
    void invalidJwt_responseUsesGenericMessage_notRawJwtExceptionText() throws Exception {
        when(jwtService.parseAndValidateAccessToken("x")).thenThrow(new JwtException("sensitive detail"));

        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setServletPath("/api/v1/x");
        req.addHeader("Authorization", "Bearer x");
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilter(req, res, filterChain);

        assertThat(res.getContentAsString()).contains("Invalid or expired token");
        assertThat(res.getContentAsString()).doesNotContain("sensitive detail");
    }
}
