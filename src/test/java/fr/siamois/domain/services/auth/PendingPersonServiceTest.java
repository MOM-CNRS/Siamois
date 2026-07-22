package fr.siamois.domain.services.auth;

import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.auth.pending.PendingPerson;
import fr.siamois.domain.models.institution.Institution;
import fr.siamois.domain.services.LangService;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.infrastructure.database.repositories.person.PendingPersonRepository;
import fr.siamois.mapper.PersonMapper;
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
import java.util.List;
import java.util.Optional;
import java.util.Set;

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

    @Mock
    private PersonMapper personMapper;

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
    void createOrGetInvitation_shouldReturnExistingInvitation() {
        PersonDTO personDTO = new PersonDTO();
        personDTO.setId(1L);
        when(pendingPersonRepository.findByDisabledPersonId(1L)).thenReturn(Optional.of(pendingPerson));

        PendingPerson result = pendingPersonService.createOrGetInvitation(personDTO);

        assertEquals(pendingPerson, result);
        verify(pendingPersonRepository, never()).save(any(PendingPerson.class));
    }

    @Test
    void createOrGetInvitation_shouldCreateNewInvitation() {
        PersonDTO personDTO = new PersonDTO();
        personDTO.setId(1L);
        when(pendingPersonRepository.findByDisabledPersonId(1L)).thenReturn(Optional.empty());
        when(pendingPersonRepository.existsByRegisterToken(anyString())).thenReturn(false);
        when(personMapper.invertConvert(personDTO)).thenReturn(person);
        when(pendingPersonRepository.save(any(PendingPerson.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PendingPerson result = pendingPersonService.createOrGetInvitation(personDTO);

        assertEquals(person, result.getDisabledPerson());
        assertNotNull(result.getRegisterToken());
        assertEquals(PendingPersonService.TOKEN_LENGTH, result.getRegisterToken().length());
        assertNotNull(result.getPendingInvitationExpirationDate());
        assertTrue(result.getPendingInvitationExpirationDate().isAfter(OffsetDateTime.now(ZoneOffset.UTC)));
    }

    @Test
    void hasPendingInvitation_shouldDelegateToRepository() {
        when(pendingPersonRepository.existsByDisabledPersonId(1L)).thenReturn(true);

        assertTrue(pendingPersonService.hasPendingInvitation(1L));
        verify(pendingPersonRepository).existsByDisabledPersonId(1L);
    }

    @Test
    void findPersonIdsWithPendingInvitation_shouldReturnMatchingIds() {
        List<Long> personIds = List.of(1L, 2L, 3L);
        when(pendingPersonRepository.findDisabledPersonIdsIn(personIds)).thenReturn(Set.of(2L));

        Set<Long> result = pendingPersonService.findPersonIdsWithPendingInvitation(personIds);

        assertEquals(Set.of(2L), result);
    }

    @Test
    void findPersonIdsWithPendingInvitation_shouldReturnEmptySetWithoutQueryOnEmptyInput() {
        Set<Long> result = pendingPersonService.findPersonIdsWithPendingInvitation(List.of());

        assertTrue(result.isEmpty());
        verify(pendingPersonRepository, never()).findDisabledPersonIdsIn(any());
    }

    @Test
    void findPersonIdsWithExpiredInvitation_shouldReturnMatchingIds() {
        List<Long> personIds = List.of(1L, 2L, 3L);
        when(pendingPersonRepository.findExpiredDisabledPersonIdsIn(eq(personIds), any(OffsetDateTime.class)))
                .thenReturn(Set.of(3L));

        Set<Long> result = pendingPersonService.findPersonIdsWithExpiredInvitation(personIds);

        assertEquals(Set.of(3L), result);
    }

    @Test
    void findPersonIdsWithExpiredInvitation_shouldReturnEmptySetWithoutQueryOnEmptyInput() {
        Set<Long> result = pendingPersonService.findPersonIdsWithExpiredInvitation(List.of());

        assertTrue(result.isEmpty());
        verify(pendingPersonRepository, never()).findExpiredDisabledPersonIdsIn(any(), any());
    }

    @Test
    void isExpired_shouldReturnTrueForPastExpirationDate() {
        pendingPerson.setPendingInvitationExpirationDate(OffsetDateTime.now(ZoneOffset.UTC).minusDays(1));

        assertTrue(pendingPersonService.isExpired(pendingPerson));
    }

    @Test
    void isExpired_shouldReturnFalseForFutureExpirationDate() {
        pendingPerson.setPendingInvitationExpirationDate(OffsetDateTime.now(ZoneOffset.UTC).plusDays(1));

        assertFalse(pendingPersonService.isExpired(pendingPerson));
    }

    @Test
    void resendInvitation_shouldReplaceTokenAndExpirationOfExistingInvitation() {
        PersonDTO personDTO = new PersonDTO();
        personDTO.setId(1L);
        String previousToken = pendingPerson.getRegisterToken();
        when(pendingPersonRepository.findByDisabledPersonId(1L)).thenReturn(Optional.of(pendingPerson));
        when(pendingPersonRepository.existsByRegisterToken(anyString())).thenReturn(false);
        when(pendingPersonRepository.save(any(PendingPerson.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PendingPerson result = pendingPersonService.resendInvitation(personDTO);

        assertNotEquals(previousToken, result.getRegisterToken());
        assertTrue(result.getPendingInvitationExpirationDate().isAfter(OffsetDateTime.now(ZoneOffset.UTC)));
        verify(personMapper, never()).invertConvert(any());
    }

    @Test
    void resendInvitation_shouldCreateInvitationWhenNoneExists() {
        PersonDTO personDTO = new PersonDTO();
        personDTO.setId(1L);
        when(pendingPersonRepository.findByDisabledPersonId(1L)).thenReturn(Optional.empty());
        when(pendingPersonRepository.existsByRegisterToken(anyString())).thenReturn(false);
        when(personMapper.invertConvert(personDTO)).thenReturn(person);
        when(pendingPersonRepository.save(any(PendingPerson.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PendingPerson result = pendingPersonService.resendInvitation(personDTO);

        assertEquals(person, result.getDisabledPerson());
        assertNotNull(result.getRegisterToken());
        assertTrue(result.getPendingInvitationExpirationDate().isAfter(OffsetDateTime.now(ZoneOffset.UTC)));
    }

    @Test
    void deleteByPerson_shouldDeleteByDisabledPersonId() {
        PersonDTO personDTO = new PersonDTO();
        personDTO.setId(1L);

        pendingPersonService.deleteByPerson(personDTO);

        verify(pendingPersonRepository).deleteByDisabledPersonId(1L);
    }

    @Test
    void findByToken_shouldReturnPendingPerson() {
        when(pendingPersonRepository.findByRegisterToken("testToken")).thenReturn(Optional.of(pendingPerson));

        Optional<PendingPerson> result = pendingPersonService.findByToken("testToken");

        assertTrue(result.isPresent());
        assertEquals(pendingPerson, result.get());
    }

}