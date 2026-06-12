package fr.siamois.domain.services.auth;

import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.domain.models.auth.pending.PendingActionUnitAttribution;
import fr.siamois.domain.models.auth.pending.PendingInstitutionInvite;
import fr.siamois.domain.models.auth.pending.PendingPerson;
import fr.siamois.domain.models.institution.Institution;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.services.LangService;
import fr.siamois.infrastructure.database.repositories.person.PendingActionUnitRepository;
import fr.siamois.infrastructure.database.repositories.person.PendingInstitutionInviteRepository;
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
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PendingPersonServiceTest {

    @Mock
    private HttpServletRequest httpServletRequest;

    @Mock
    private PendingInstitutionInviteRepository pendingInstitutionInviteRepository;

    @Mock
    private EmailManager emailManager;

    @Mock
    private LangService langService;

    @Mock
    private PendingActionUnitRepository pendingActionUnitRepository;

    @Mock
    private PendingPersonRepository pendingPersonRepository;

    @InjectMocks
    private PendingPersonService pendingPersonService;

    private PendingPerson pendingPerson;
    private Institution institution;

    @BeforeEach
    void setUp() {
        pendingPerson = new PendingPerson();
        pendingPerson.setEmail("test@example.com");
        pendingPerson.setRegisterToken("testToken");
        pendingPerson.setPendingInvitationExpirationDate(OffsetDateTime.now(ZoneOffset.UTC).plusDays(3));

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
    void createOrGetPendingPerson_shouldReturnExistingPerson() {
        when(pendingPersonRepository.findByEmail("test@example.com")).thenReturn(Optional.of(pendingPerson));

        PendingPerson result = pendingPersonService.createOrGetPendingPerson("test@example.com");

        assertEquals(pendingPerson, result);
    }

    @Test
    void createOrGetPendingPerson_shouldCreateNewPerson() {
        when(pendingPersonRepository.findByEmail("test@example.com")).thenReturn(Optional.empty());
        when(pendingPersonRepository.save(any(PendingPerson.class))).thenReturn(pendingPerson);

        PendingPerson result = pendingPersonService.createOrGetPendingPerson("test@example.com");

        assertNotNull(result);
        assertEquals("test@example.com", result.getEmail());
    }

    @Test
    void sendPendingManagerInstitutionInvite_shouldReturnFalseIfInviteExists() {
        when(pendingInstitutionInviteRepository.findByInstitutionAndPendingPerson(institution, pendingPerson))
                .thenReturn(Optional.of(new PendingInstitutionInvite()));

        boolean result = pendingPersonService.sendPendingManagerInstitutionInvite(pendingPerson, institution, true, "en");

        assertFalse(result);
    }

    @Test
    void sendPendingManagerInstitutionInvite_shouldReturnTrueIfInviteDoesNotExist() {
        when(pendingInstitutionInviteRepository.findByInstitutionAndPendingPerson(institution, pendingPerson))
                .thenReturn(Optional.empty());

        when(langService.msg(anyString(), any(Locale.class), anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        when(langService.msg(anyString(), any(Locale.class), anyString(), anyString(), anyString(), anyString())).thenAnswer(invocation -> invocation.getArgument(0));


        boolean result = pendingPersonService.sendPendingManagerInstitutionInvite(pendingPerson, institution, true, "en");

        assertTrue(result);
        verify(emailManager, times(1)).sendEmail(anyString(), anyString(), anyString());
    }

    @Test
    void sendPendingActionManagerInstitutionInvite_shouldReturnFalseIfInviteExists() {
        when(pendingInstitutionInviteRepository.findByInstitutionAndPendingPerson(institution, pendingPerson))
                .thenReturn(Optional.of(new PendingInstitutionInvite()));

        boolean result = pendingPersonService.sendPendingActionManagerInstitutionInvite(pendingPerson, institution, "en");

        assertFalse(result);
    }

    @Test
    void sendPendingActionManagerInstitutionInvite_shouldReturnTrueIfInviteDoesNotExist() {
        when(pendingInstitutionInviteRepository.findByInstitutionAndPendingPerson(institution, pendingPerson))
                .thenReturn(Optional.empty());

        when(langService.msg(anyString(), any(Locale.class), anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        when(langService.msg(anyString(), any(Locale.class), anyString(), anyString(), anyString(), anyString())).thenAnswer(invocation -> invocation.getArgument(0));

        boolean result = pendingPersonService.sendPendingActionManagerInstitutionInvite(pendingPerson, institution, "en");

        assertTrue(result);
        verify(emailManager, times(1)).sendEmail(anyString(), anyString(), anyString());
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

    @Test
    void findInstitutionsByPendingPerson_shouldReturnInvites() {
        Set<PendingInstitutionInvite> invites = Set.of(new PendingInstitutionInvite());
        when(pendingInstitutionInviteRepository.findAllByPendingPerson(pendingPerson)).thenReturn(invites);

        Set<PendingInstitutionInvite> result = pendingPersonService.findInstitutionsByPendingPerson(pendingPerson);

        assertEquals(invites, result);
    }

    @Test
    void findActionAttributionsByPendingInvite_shouldReturnAttributions() {
        PendingInstitutionInvite invite = new PendingInstitutionInvite();
        Set<PendingActionUnitAttribution> attributions = Set.of(new PendingActionUnitAttribution());
        when(pendingActionUnitRepository.findByInstitutionInvite(invite)).thenReturn(attributions);

        Set<PendingActionUnitAttribution> result = pendingPersonService.findActionAttributionsByPendingInvite(invite);

        assertEquals(attributions, result);
    }

    @Test
    void sendPendingActionMemberInvite_shouldReturnFalseIfInviteExists() {
        ActionUnit actionUnit = new ActionUnit();
        actionUnit.setId(1L);
        actionUnit.setCreatedByInstitution(institution);

        Concept role = new Concept();
        role.setId(1L);

        PendingInstitutionInvite existingInvite = new PendingInstitutionInvite();
        existingInvite.setInstitution(institution);

        when(pendingInstitutionInviteRepository.findByInstitutionAndPendingPerson(institution, pendingPerson))
                .thenReturn(Optional.of(existingInvite));

        boolean result = pendingPersonService.sendPendingActionMemberInvite(pendingPerson, actionUnit, role, "en");

        assertFalse(result);
        verify(emailManager, never()).sendEmail(anyString(), anyString(), anyString());
    }

    @Test
    void sendPendingActionMemberInvite_shouldReturnTrueIfInviteDoesNotExist() {
        ActionUnit actionUnit = new ActionUnit();
        actionUnit.setId(1L);
        actionUnit.setCreatedByInstitution(institution);

        Concept role = new Concept();
        role.setId(1L);

        when(pendingInstitutionInviteRepository.findByInstitutionAndPendingPerson(institution, pendingPerson))
                .thenReturn(Optional.empty());
        when(pendingInstitutionInviteRepository.save(any(PendingInstitutionInvite.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(langService.msg(anyString(), any(Locale.class), anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        when(langService.msg(anyString(), any(Locale.class), anyString(), anyString(), anyString(), anyString()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        boolean result = pendingPersonService.sendPendingActionMemberInvite(pendingPerson, actionUnit, role, "en");

        assertTrue(result);
        verify(pendingActionUnitRepository, times(1)).save(any(PendingActionUnitAttribution.class));
        verify(emailManager, times(1)).sendEmail(anyString(), anyString(), anyString());
    }

    @Test
    void delete_shouldRemovePendingInstitutionInvite() {
        PendingInstitutionInvite invite = new PendingInstitutionInvite();
        invite.setId(1L);

        pendingPersonService.delete(invite);

        verify(pendingInstitutionInviteRepository, times(1)).delete(invite);
    }

    @Test
    void delete_shouldRemovePendingActionUnitAttribution() {
        PendingActionUnitAttribution attribution = new PendingActionUnitAttribution();
        attribution.setId(new PendingActionUnitAttribution.PendingActionUnitId());
        attribution.getId().setActionUnitId(1L);
        attribution.getId().setPendingInstitutionInviteId(1L);

        pendingPersonService.delete(attribution);

        verify(pendingActionUnitRepository, times(1)).delete(attribution);
    }

}