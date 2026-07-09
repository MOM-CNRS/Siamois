package fr.siamois.domain.services.auth;

import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.auth.pending.PendingPerson;
import fr.siamois.domain.models.institution.Institution;
import fr.siamois.domain.services.LangService;
import fr.siamois.infrastructure.database.repositories.person.PendingPersonRepository;
import fr.siamois.ui.email.EmailManager;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PendingPersonServiceTest {
    private static final OffsetDateTime NOW = OffsetDateTime.of(2024, 1, 15, 12, 0, 0, 0, ZoneOffset.UTC);


    @Mock
    private HttpServletRequest httpServletRequest;

    @Mock
    private EmailManager emailManager;

    @Mock
    private LangService langService;

    @Mock
    private PendingPersonRepository pendingPersonRepository;

    @InjectMocks
    private PendingPersonService pendingPersonService;

    private Person person;
    private PendingPerson pendingPerson;
    private Institution institution;

    @BeforeEach
    void setUp() {
        person = new Person();
        person.setId(1L);
        person.setUsername("username");
        person.setEmail("email");
        person.setPassword("password");
        person.setEnabled(false);

        pendingPerson = new PendingPerson();
        pendingPerson.setRegisterToken("testToken");
        pendingPerson.setPendingInvitationExpirationDate(NOW.plusDays(3));
        pendingPerson.setDisabledPerson(person);

        institution = new Institution();
        institution.setName("Test Institution");
        institution.setIdentifier("1212");
    }

    @Test
    void generateToken_shouldReturnUniqueToken() {
        when(pendingPersonRepository.existsByRegisterToken(anyString())).thenReturn(false);

        String token = pendingPersonService.generateToken();

        assertNotNull(token);
        assertEquals(PendingPersonService.TOKEN_LENGTH, token.length());
    }

    @Test
    void generateToken_shouldThrowExceptionAfterMaxAttempts() {
        when(pendingPersonRepository.existsByRegisterToken(anyString())).thenReturn(true);

        assertThrows(IllegalStateException.class, () -> pendingPersonService.generateToken());
    }

    @Test
    void invitationLink_shouldReturnCorrectLink() {
        when(httpServletRequest.getScheme()).thenReturn("http");
        when(httpServletRequest.getServerName()).thenReturn("localhost");
        when(httpServletRequest.getServerPort()).thenReturn(8080);
        when(httpServletRequest.getContextPath()).thenReturn("/app");

        String link = pendingPersonService.invitationLink(pendingPerson);

        assertEquals("http://localhost:8080/app/register/testToken", link);
    }

    @Test
    void delete_shouldRemovePendingPerson() {
        pendingPersonService.delete(pendingPerson);

        verify(pendingPersonRepository, times(1)).delete(pendingPerson);
    }

    @Test
    void findByToken_shouldReturnPendingPerson() {
        when(pendingPersonRepository.findByRegisterToken("testToken")).thenReturn(Optional.of(pendingPerson));

        Optional<PendingPerson> result = pendingPersonService.findByToken("testToken");

        assertTrue(result.isPresent());
        assertEquals(pendingPerson, result.get());
    }

}