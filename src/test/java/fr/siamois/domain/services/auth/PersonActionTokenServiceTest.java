package fr.siamois.domain.services.auth;

import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.auth.token.PersonActionToken;
import fr.siamois.domain.models.auth.token.PersonActionType;
import fr.siamois.infrastructure.database.repositories.person.PersonActionTokenRepository;
import fr.siamois.infrastructure.database.repositories.person.PersonRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PersonActionTokenServiceTest {

    @Mock
    private PersonActionTokenRepository tokenRepository;
    @Mock
    private PersonRepository personRepository;
    @Mock
    private HttpServletRequest request;
    private PersonActionTokenService service;

    @BeforeEach
    void setUp() {
        service = new PersonActionTokenService(tokenRepository, personRepository, request);
    }

    @Test
    void issue_replacesPreviousTokenAndSetsExpiration() {
        Person person = new Person();
        person.setId(1L);
        when(personRepository.findById(Long.valueOf(1L))).thenReturn(Optional.of(person));
        when(tokenRepository.existsByToken(any())).thenReturn(false);
        when(tokenRepository.save(any(PersonActionToken.class))).thenAnswer(i -> i.getArgument(0));

        PersonActionToken token = service.issue(1L, PersonActionType.EMAIL_CHANGE, "new@mail.com");

        verify(tokenRepository).deleteByPersonIdAndType(1L, PersonActionType.EMAIL_CHANGE);
        assertEquals(PersonActionTokenService.TOKEN_LENGTH, token.getToken().length());
        assertEquals("new@mail.com", token.getNewEmail());
        assertTrue(token.getExpirationDate().isAfter(OffsetDateTime.now(ZoneOffset.UTC).plusHours(23)));
    }

    @Test
    void issue_passwordResetIgnoresNewEmail() {
        Person person = new Person();
        person.setId(1L);
        when(personRepository.findById(Long.valueOf(1L))).thenReturn(Optional.of(person));
        when(tokenRepository.existsByToken(any())).thenReturn(false);
        when(tokenRepository.save(any(PersonActionToken.class))).thenAnswer(i -> i.getArgument(0));

        PersonActionToken token = service.issue(1L, PersonActionType.PASSWORD_RESET, "ignored@mail.com");

        assertNull(token.getNewEmail());
    }

    @Test
    void findValid_filtersExpiredTokens() {
        PersonActionToken expired = new PersonActionToken();
        expired.setExpirationDate(OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1));
        when(tokenRepository.findByTokenAndType("t", PersonActionType.PASSWORD_RESET)).thenReturn(Optional.of(expired));

        assertTrue(service.findValid("t", PersonActionType.PASSWORD_RESET).isEmpty());
    }

    @Test
    void canIssue_falseWhenPreviousTokenIsTooRecent() {
        PersonActionToken recent = new PersonActionToken();
        recent.setCreationDate(OffsetDateTime.now(ZoneOffset.UTC).minusSeconds(10));
        when(tokenRepository.findByPersonIdAndType(1L, PersonActionType.PASSWORD_RESET)).thenReturn(Optional.of(recent));

        assertFalse(service.canIssue(1L, PersonActionType.PASSWORD_RESET));
    }

    @Test
    void canIssue_trueWhenNoPreviousToken() {
        when(tokenRepository.findByPersonIdAndType(1L, PersonActionType.PASSWORD_RESET)).thenReturn(Optional.empty());

        assertTrue(service.canIssue(1L, PersonActionType.PASSWORD_RESET));
    }

    @Test
    void findValid_returnsTokenWhenNotExpired() {
        PersonActionToken valid = new PersonActionToken();
        valid.setExpirationDate(OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(5));
        when(tokenRepository.findByTokenAndType("t", PersonActionType.EMAIL_CHANGE)).thenReturn(Optional.of(valid));

        assertEquals(Optional.of(valid), service.findValid("t", PersonActionType.EMAIL_CHANGE));
    }

    @Test
    void canIssue_trueWhenPreviousTokenIsOldEnough() {
        PersonActionToken old = new PersonActionToken();
        old.setCreationDate(OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(5));
        when(tokenRepository.findByPersonIdAndType(1L, PersonActionType.EMAIL_CHANGE)).thenReturn(Optional.of(old));

        assertTrue(service.canIssue(1L, PersonActionType.EMAIL_CHANGE));
    }

    @Test
    void issue_throwsWhenPersonUnknown() {
        when(personRepository.findById(Long.valueOf(9L))).thenReturn(Optional.empty());

        assertThrows(jakarta.persistence.EntityNotFoundException.class,
                () -> service.issue(9L, PersonActionType.PASSWORD_RESET, null));
    }

    @Test
    void generateToken_retriesWhileTokenAlreadyExists() {
        when(tokenRepository.existsByToken(any())).thenReturn(true, false);

        String token = service.generateToken();

        assertEquals(PersonActionTokenService.TOKEN_LENGTH, token.length());
        verify(tokenRepository, times(2)).existsByToken(any());
    }

    @Test
    void delete_delegatesToRepository() {
        PersonActionToken token = new PersonActionToken();

        service.delete(token);

        verify(tokenRepository).delete(token);
    }

    @Test
    void validityOf_isShorterForPasswordReset() {
        assertTrue(PersonActionTokenService.validityOf(PersonActionType.PASSWORD_RESET)
                .compareTo(PersonActionTokenService.validityOf(PersonActionType.EMAIL_CHANGE)) < 0);
    }

    @Test
    void link_buildsResetPasswordUrlWithoutCommonPort() {
        when(request.getScheme()).thenReturn("https");
        when(request.getServerName()).thenReturn("siamois.eu");
        when(request.getServerPort()).thenReturn(443);
        when(request.getContextPath()).thenReturn("");
        PersonActionToken token = new PersonActionToken();
        token.setType(PersonActionType.PASSWORD_RESET);
        token.setToken("abc");

        assertEquals("https://siamois.eu/reset-password/abc", service.link(token));
    }

    @Test
    void link_buildsConfirmEmailUrlWithCustomPortAndContext() {
        when(request.getScheme()).thenReturn("http");
        when(request.getServerName()).thenReturn("localhost");
        when(request.getServerPort()).thenReturn(8080);
        when(request.getContextPath()).thenReturn("/siamois");
        PersonActionToken token = new PersonActionToken();
        token.setType(PersonActionType.EMAIL_CHANGE);
        token.setToken("xyz");

        assertEquals("http://localhost:8080/siamois/confirm-email/xyz", service.link(token));
    }
}
