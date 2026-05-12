package fr.siamois.ui.service.auth;

import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.auth.RefreshToken;
import fr.siamois.domain.services.InstitutionService;
import fr.siamois.domain.services.auth.TokenHasher;
import fr.siamois.domain.services.person.PersonDetailsService;
import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.infrastructure.database.repositories.auth.RefreshTokenRepository;
import fr.siamois.mapper.PersonMapper;
import fr.siamois.ui.api.openapi.v1.auth.dto.LoginResponse;
import fr.siamois.ui.api.openapi.v1.auth.dto.TokenRefreshResponse;
import fr.siamois.ui.config.security.jwt.JwtProperties;
import fr.siamois.ui.config.security.jwt.JwtService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private PersonDetailsService personDetailsService;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtService jwtService;
    @Mock
    private JwtProperties jwtProperties;
    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @Mock
    private InstitutionService institutionService;
    @Mock
    private PersonMapper personMapper;

    @InjectMocks
    private AuthService authService;

    private Person person;

    @BeforeEach
    void setUp() {
        person = new Person();
        person.setId(99L);
        person.setEmail("user@test.fr");
        person.setUsername("user");
        person.setName("N");
        person.setLastname("L");
        person.setPassword("{bcrypt}hash");
        person.setEnabled(true);
        person.setExpired(false);
        person.setLocked(false);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void login_success_returnsTokensAndPersistsRefreshHash() {
        when(personDetailsService.findPersonByEmail("user@test.fr")).thenReturn(person);
        when(passwordEncoder.matches("pwd", person.getPassword())).thenReturn(true);
        when(jwtService.createAccessToken(person)).thenReturn("jwt-access");
        when(jwtService.accessTokenExpiresInSeconds()).thenReturn(900L);
        when(jwtProperties.getRefreshTokenValidity()).thenReturn(Duration.ofDays(30));

        PersonDTO dto = new PersonDTO();
        dto.setId(99L);
        when(personMapper.convert(person)).thenReturn(dto);
        when(institutionService.findInstitutionsOfPerson(dto)).thenReturn(Set.of());

        LoginResponse response = authService.login("user@test.fr", "pwd");

        assertThat(response.accessToken()).isEqualTo("jwt-access");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresIn()).isEqualTo(900L);
        assertThat(response.refreshToken()).isNotBlank();
        assertThat(response.user().email()).isEqualTo("user@test.fr");

        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(captor.capture());
        RefreshToken saved = captor.getValue();
        assertThat(saved.getTokenHash()).isEqualTo(TokenHasher.sha256Hex(response.refreshToken()));
        assertThat(saved.getPerson()).isSameAs(person);
    }

    @Test
    void login_missingCredentials_throws() {
        assertThatThrownBy(() -> authService.login("", "x"))
                .isInstanceOf(BadCredentialsException.class);
        assertThatThrownBy(() -> authService.login("a@b.fr", null))
                .isInstanceOf(BadCredentialsException.class);
        assertThatThrownBy(() -> authService.login(null, "x"))
                .isInstanceOf(BadCredentialsException.class);
        assertThatThrownBy(() -> authService.login("   ", "x"))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void login_trimsEmailBeforeLookup() {
        when(personDetailsService.findPersonByEmail("trim@test.fr")).thenReturn(person);
        when(passwordEncoder.matches("pwd", person.getPassword())).thenReturn(true);
        when(jwtService.createAccessToken(person)).thenReturn("jwt");
        when(jwtService.accessTokenExpiresInSeconds()).thenReturn(900L);
        when(jwtProperties.getRefreshTokenValidity()).thenReturn(Duration.ofDays(30));
        PersonDTO dto = new PersonDTO();
        when(personMapper.convert(person)).thenReturn(dto);
        when(institutionService.findInstitutionsOfPerson(dto)).thenReturn(Set.of());

        authService.login("  trim@test.fr  ", "pwd");

        verify(personDetailsService).findPersonByEmail("trim@test.fr");
    }

    @Test
    void login_lockedAccount_throws() {
        person.setLocked(true);
        when(personDetailsService.findPersonByEmail("user@test.fr")).thenReturn(person);
        when(passwordEncoder.matches("pwd", person.getPassword())).thenReturn(true);

        assertThatThrownBy(() -> authService.login("user@test.fr", "pwd"))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("disabled");
    }

    @Test
    void login_expiredAccount_throws() {
        person.setExpired(true);
        when(personDetailsService.findPersonByEmail("user@test.fr")).thenReturn(person);
        when(passwordEncoder.matches("pwd", person.getPassword())).thenReturn(true);

        assertThatThrownBy(() -> authService.login("user@test.fr", "pwd"))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("disabled");
    }

    @Test
    void login_success_sortedOrganizations_andFiltersNullInstitutionIds() {
        when(personDetailsService.findPersonByEmail("user@test.fr")).thenReturn(person);
        when(passwordEncoder.matches("pwd", person.getPassword())).thenReturn(true);
        when(jwtService.createAccessToken(person)).thenReturn("jwt");
        when(jwtService.accessTokenExpiresInSeconds()).thenReturn(900L);
        when(jwtProperties.getRefreshTokenValidity()).thenReturn(Duration.ofDays(30));

        PersonDTO dto = new PersonDTO();
        when(personMapper.convert(person)).thenReturn(dto);

        InstitutionDTO z = new InstitutionDTO();
        z.setId(2L);
        z.setName("Zoo");
        InstitutionDTO a = new InstitutionDTO();
        a.setId(3L);
        a.setName("alpha");
        InstitutionDTO nullId = new InstitutionDTO();
        nullId.setId(null);
        nullId.setName("skip");

        Set<InstitutionDTO> unsorted = new LinkedHashSet<>();
        unsorted.add(z);
        unsorted.add(nullId);
        unsorted.add(a);
        when(institutionService.findInstitutionsOfPerson(dto)).thenReturn(unsorted);

        LoginResponse response = authService.login("user@test.fr", "pwd");

        assertThat(response.user().organizations()).hasSize(2);
        assertThat(response.user().organizations().get(0).name()).isEqualTo("alpha");
        assertThat(response.user().organizations().get(1).name()).isEqualTo("Zoo");
    }

    @Test
    void login_unknownEmail_throwsBadCredentials() {
        when(personDetailsService.findPersonByEmail("x@y.fr"))
                .thenThrow(new UsernameNotFoundException("no"));

        assertThatThrownBy(() -> authService.login("x@y.fr", "pwd"))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("Invalid credentials");
    }

    @Test
    void login_wrongPassword_throws() {
        when(personDetailsService.findPersonByEmail("user@test.fr")).thenReturn(person);
        when(passwordEncoder.matches("bad", person.getPassword())).thenReturn(false);

        assertThatThrownBy(() -> authService.login("user@test.fr", "bad"))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void login_disabledAccount_throws() {
        person.setEnabled(false);
        when(personDetailsService.findPersonByEmail("user@test.fr")).thenReturn(person);
        when(passwordEncoder.matches("pwd", person.getPassword())).thenReturn(true);

        assertThatThrownBy(() -> authService.login("user@test.fr", "pwd"))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("disabled");
    }

    @Test
    void refresh_success_returnsNewAccess() {
        String opaque = "opaque-refresh-value";
        String hash = TokenHasher.sha256Hex(opaque);
        RefreshToken stored = new RefreshToken();
        stored.setPerson(person);
        stored.setExpiresAt(OffsetDateTime.now().plusHours(1));

        when(refreshTokenRepository.findByTokenHash(hash)).thenReturn(Optional.of(stored));
        when(jwtService.createAccessToken(person)).thenReturn("new-access");
        when(jwtService.accessTokenExpiresInSeconds()).thenReturn(900L);

        TokenRefreshResponse r = authService.refresh(opaque);

        assertThat(r.accessToken()).isEqualTo("new-access");
        assertThat(r.tokenType()).isEqualTo("Bearer");
        assertThat(r.expiresIn()).isEqualTo(900L);
    }

    @Test
    void refresh_missingToken_throws() {
        assertThatThrownBy(() -> authService.refresh("  "))
                .isInstanceOf(BadCredentialsException.class);
        assertThatThrownBy(() -> authService.refresh(null))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void refresh_trimsOpaqueBeforeHash() {
        String raw = "  my-token  ";
        String hash = TokenHasher.sha256Hex("my-token");
        RefreshToken stored = new RefreshToken();
        stored.setPerson(person);
        stored.setExpiresAt(OffsetDateTime.now().plusHours(1));

        when(refreshTokenRepository.findByTokenHash(hash)).thenReturn(Optional.of(stored));
        when(jwtService.createAccessToken(person)).thenReturn("access");
        when(jwtService.accessTokenExpiresInSeconds()).thenReturn(60L);

        authService.refresh(raw);

        verify(refreshTokenRepository).findByTokenHash(hash);
    }

    @Test
    void refresh_unknownHash_throws() {
        when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.refresh("unknown"))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void refresh_expired_deletesAndThrows() {
        String opaque = "expired-rt";
        String hash = TokenHasher.sha256Hex(opaque);
        RefreshToken stored = new RefreshToken();
        stored.setExpiresAt(OffsetDateTime.now().minusMinutes(1));

        when(refreshTokenRepository.findByTokenHash(eq(hash))).thenReturn(Optional.of(stored));

        assertThatThrownBy(() -> authService.refresh(opaque))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("Expired");

        verify(refreshTokenRepository).delete(stored);
    }

    @Test
    void logout_deletesByHash() {
        String opaque = "to-revoke";
        authService.logout(opaque);

        verify(refreshTokenRepository).deleteByTokenHash(TokenHasher.sha256Hex(opaque));
    }

    @Test
    void logout_blank_noDelete() {
        authService.logout("   ");
        verify(refreshTokenRepository, never()).deleteByTokenHash(any());
    }

    @Test
    void logout_null_noDelete() {
        authService.logout(null);
        verify(refreshTokenRepository, never()).deleteByTokenHash(any());
    }

    @Test
    void logout_trimsOpaqueBeforeDelete() {
        authService.logout("  revoke-me  ");
        verify(refreshTokenRepository).deleteByTokenHash(TokenHasher.sha256Hex("revoke-me"));
    }

    @Test
    void currentUser_withoutSecurityContext_throws401() {
        assertThatThrownBy(() -> authService.currentUser())
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void currentUser_returnsPayloadFromContext() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(person, null, person.getAuthorities()));

        PersonDTO dto = new PersonDTO();
        dto.setId(99L);
        when(personMapper.convert(person)).thenReturn(dto);

        InstitutionDTO inst = new InstitutionDTO();
        inst.setId(1L);
        inst.setName("Lab");
        when(institutionService.findInstitutionsOfPerson(dto)).thenReturn(Set.of(inst));

        var me = authService.currentUser();

        assertThat(me.id()).isEqualTo(99L);
        assertThat(me.email()).isEqualTo("user@test.fr");
        assertThat(me.organizations()).hasSize(1);
        assertThat(me.organizations().get(0).name()).isEqualTo("Lab");
    }

    @Test
    void currentUser_anonymousAuthentication_throws401() {
        SecurityContextHolder.getContext().setAuthentication(
                new AnonymousAuthenticationToken(
                        "key", "anon", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));

        assertThatThrownBy(() -> authService.currentUser())
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
