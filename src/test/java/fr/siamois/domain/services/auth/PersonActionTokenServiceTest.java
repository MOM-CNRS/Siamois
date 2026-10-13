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
}
