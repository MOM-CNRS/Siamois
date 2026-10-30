package fr.siamois.domain.services.auth;

import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.auth.invitation.InvitationLink;
import fr.siamois.domain.models.auth.invitation.InvitationLinkStatus;
import fr.siamois.domain.models.exceptions.auth.InvitationLinkUnavailableException;
import fr.siamois.domain.models.institution.Institution;
import fr.siamois.domain.services.InstitutionService;
import fr.siamois.domain.services.person.PersonService;
import fr.siamois.domain.services.vocabulary.SiamoisThesaurus;
import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.infrastructure.database.repositories.institution.InstitutionRepository;
import fr.siamois.infrastructure.database.repositories.person.InvitationLinkRepository;
import fr.siamois.infrastructure.database.repositories.person.PersonRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InvitationLinkServiceTest {

    private static final String TOKEN = "secret-token";

    @Mock
    private InvitationLinkRepository invitationLinkRepository;
    @Mock
    private PersonRepository personRepository;
    @Mock
    private PersonService personService;
    @Mock
    private InstitutionService institutionService;
    @Mock
    private InstitutionRepository institutionRepository;
    @Mock
    private SiamoisThesaurus siamoisThesaurus;
    @Mock
    private HttpServletRequest request;

    private InvitationLinkService service;

    @BeforeEach
    void setUp() {
        service = new InvitationLinkService(invitationLinkRepository, personRepository, personService,
                institutionService, institutionRepository, siamoisThesaurus, request);
        when(invitationLinkRepository.save(any(InvitationLink.class))).thenAnswer(i -> i.getArgument(0));
    }

    private static InvitationLink link(int max, Duration expiresIn, boolean revoked) {
        InvitationLink l = new InvitationLink();
        l.setId(5L);
        l.setMaxAccounts(max);
        l.setCreationDate(OffsetDateTime.now(ZoneOffset.UTC).minusHours(1));
        l.setExpirationDate(OffsetDateTime.now(ZoneOffset.UTC).plus(expiresIn));
        l.setRevoked(revoked);
        return l;
    }

    @Test
    void create_storesOnlyTheHashOfTheToken() {
        when(personRepository.findById(Long.valueOf(1L))).thenReturn(Optional.of(new Person()));

        InvitationLinkService.Created created = service.create(Duration.ofHours(24), 3, 1L);

        assertEquals(40, created.rawToken().length());
        assertEquals(InvitationLinkService.hash(created.rawToken()), created.link().getTokenHash());
        assertNotEquals(created.rawToken(), created.link().getTokenHash());
        assertEquals(3, created.link().getMaxAccounts());
        assertTrue(created.link().getExpirationDate().isAfter(OffsetDateTime.now(ZoneOffset.UTC).plusHours(23)));
    }

    @Test
    void create_rejectsOutOfBoundsValues() {
        assertThrows(IllegalArgumentException.class, () -> service.create(Duration.ofDays(31), 1, 1L));
        assertThrows(IllegalArgumentException.class, () -> service.create(Duration.ZERO, 1, 1L));
        assertThrows(IllegalArgumentException.class, () -> service.create(Duration.ofHours(1), 0, 1L));
        assertThrows(IllegalArgumentException.class, () -> service.create(Duration.ofHours(1), 101, 1L));
        verify(invitationLinkRepository, never()).save(any());
    }

    @Test
    void create_acceptsTheLimits() {
        when(personRepository.findById(Long.valueOf(1L))).thenReturn(Optional.of(new Person()));
        assertDoesNotThrow(() -> service.create(InvitationLinkService.MAX_VALIDITY, 100, 1L));
        assertDoesNotThrow(() -> service.create(InvitationLinkService.DEFAULT_VALIDITY, 1, 1L));
    }

    @Test
    void statusOf_coversEveryCase() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        assertEquals(InvitationLinkStatus.ACTIVE, InvitationLinkService.statusOf(link(2, Duration.ofHours(1), false), 1, now));
        assertEquals(InvitationLinkStatus.FULL, InvitationLinkService.statusOf(link(2, Duration.ofHours(1), false), 2, now));
        assertEquals(InvitationLinkStatus.EXPIRED, InvitationLinkService.statusOf(link(2, Duration.ofMinutes(-1), false), 0, now));
        assertEquals(InvitationLinkStatus.REVOKED, InvitationLinkService.statusOf(link(2, Duration.ofHours(1), true), 0, now));
    }

    @Test
    void isUsable_falseWhenUnknownOrFull() {
        assertFalse(service.isUsable(null));
        assertFalse(service.isUsable("unknown"));

        when(invitationLinkRepository.findByTokenHash(InvitationLinkService.hash(TOKEN)))
                .thenReturn(Optional.of(link(1, Duration.ofHours(1), false)));
        when(personRepository.countByInvitationLinkId(5L)).thenReturn(1L);
        assertFalse(service.isUsable(TOKEN));

        when(personRepository.countByInvitationLinkId(5L)).thenReturn(0L);
        assertTrue(service.isUsable(TOKEN));
    }

    @Test
    void revoke_flagsTheLink() {
        InvitationLink l = link(1, Duration.ofHours(1), false);
        when(invitationLinkRepository.findById(Long.valueOf(5L))).thenReturn(Optional.of(l));

        service.revoke(5L);

        assertTrue(l.isRevoked());
    }

    @Test
    void findAll_computesCountsAndStatus() {
        InvitationLink full = link(1, Duration.ofHours(1), false);
        when(invitationLinkRepository.findAllByOrderByCreationDateDesc()).thenReturn(List.of(full));
        when(personRepository.countByInvitationLinkIds(any())).thenReturn(List.<Object[]>of(new Object[]{5L, 1L}));

        List<InvitationLinkService.Summary> all = service.findAll();

        assertEquals(1, all.size());
        assertEquals(1, all.get(0).createdAccounts());
        assertEquals(InvitationLinkStatus.FULL, all.get(0).status());
    }

    @Test
    void register_unknownLinkCreatesNothing() {
        when(invitationLinkRepository.findByTokenHashForUpdate(anyString())).thenReturn(Optional.empty());

        assertThrows(InvitationLinkUnavailableException.class, () -> service.register(TOKEN, new PersonDTO(), "pw"));

        verifyNoInteractions(personService, institutionService);
    }

    @Test
    void register_fullLinkCreatesNothing() throws Exception {
        when(invitationLinkRepository.findByTokenHashForUpdate(InvitationLinkService.hash(TOKEN)))
                .thenReturn(Optional.of(link(2, Duration.ofHours(1), false)));
        when(personRepository.countByInvitationLinkId(5L)).thenReturn(2L);

        assertThrows(InvitationLinkUnavailableException.class, () -> service.register(TOKEN, new PersonDTO(), "pw"));

        verify(personService, never()).createPerson(any(), anyString());
    }

    @Test
    void register_createsUnverifiedAccountWithPersonalOrganization() throws Exception {
        InvitationLink l = link(2, Duration.ofHours(1), false);
        when(invitationLinkRepository.findByTokenHashForUpdate(InvitationLinkService.hash(TOKEN))).thenReturn(Optional.of(l));
        when(personRepository.countByInvitationLinkId(5L)).thenReturn(1L);

        PersonDTO dto = new PersonDTO();
        dto.setId(9L);
        Person person = new Person();
        person.setId(9L);
        person.setName("Ada");
        person.setLastname("Lovelace");
        person.setUsername("ada.lovelace");
        when(personService.generateUniqueUsername(any(), any(), any(), any())).thenReturn("ada.lovelace");
        when(personService.createPerson(any(), anyString())).thenReturn(dto);
        when(personRepository.findById(Long.valueOf(9L))).thenReturn(Optional.of(person));
        when(personRepository.save(any(Person.class))).thenAnswer(i -> i.getArgument(0));

        InstitutionDTO institutionDto = new InstitutionDTO();
        institutionDto.setId(30L);
        Institution institution = new Institution();
        when(siamoisThesaurus.url()).thenReturn("https://thesaurus.mom.fr/?idt=th337");
        when(institutionService.findByIdentifier(anyString())).thenReturn(Optional.empty());
        when(institutionService.createInstitution(any(InstitutionDTO.class), anyString())).thenReturn(institutionDto);
        when(institutionRepository.findById(Long.valueOf(30L))).thenReturn(Optional.of(institution));

        service.register(TOKEN, new PersonDTO(), "pw");

        assertFalse(person.isMailVerified());
        ArgumentCaptor<PersonDTO> submitted = ArgumentCaptor.forClass(PersonDTO.class);
        verify(personService).createPerson(submitted.capture(), eq("pw"));
        assertEquals("ada.lovelace", submitted.getValue().getUsername());
        assertSame(l, person.getInvitationLink());
        ArgumentCaptor<InstitutionDTO> created = ArgumentCaptor.forClass(InstitutionDTO.class);
        verify(institutionService).createInstitution(created.capture(), eq("https://thesaurus.mom.fr/?idt=th337"));
        assertEquals("Organisation personnelle de Ada Lovelace", created.getValue().getName());
        assertEquals("perso-ada-lovelace", created.getValue().getIdentifier());
        verify(institutionService).addToManagers(institutionDto, dto);
        assertSame(person, institution.getPersonalOwner());
    }

    @Test
    void personalOrganizationName_isTruncatedToTheInstitutionNameLimit() {
        Person person = new Person();
        person.setName("Maximiliano");
        person.setLastname("Bartholomew-Montgomery");

        String name = InvitationLinkService.personalOrganizationName(person, 1);
        String second = InvitationLinkService.personalOrganizationName(person, 2);

        assertTrue(name.length() <= Institution.MAX_NAME_LENGTH);
        assertTrue(name.startsWith(InvitationLinkService.PERSONAL_ORGANIZATION_PREFIX));
        assertTrue(second.length() <= Institution.MAX_NAME_LENGTH);
        assertTrue(second.endsWith(" (2)"));
        assertNotEquals(name, second);
    }

    @Test
    void personalOrganizationIdentifier_isAnAsciiSlug() {
        Person person = new Person();
        person.setName("Éloïse");
        person.setLastname("D'Anjou  Saint-Léger");

        assertEquals("perso-eloise-d-anjou-saint-leger", InvitationLinkService.personalOrganizationIdentifier(person, 1));
        assertEquals("perso-eloise-d-anjou-saint-leger-3", InvitationLinkService.personalOrganizationIdentifier(person, 3));
    }

    @Test
    void register_nameAndIdentifierClashesBumpTheSameRank() throws Exception {
        InvitationLink l = link(2, Duration.ofHours(1), false);
        when(invitationLinkRepository.findByTokenHashForUpdate(anyString())).thenReturn(Optional.of(l));
        PersonDTO dto = new PersonDTO();
        dto.setId(9L);
        Person person = new Person();
        person.setName("A");
        person.setLastname("B");
        person.setUsername("ab");
        when(personService.createPerson(any(), anyString())).thenReturn(dto);
        when(personRepository.findById(Long.valueOf(9L))).thenReturn(Optional.of(person));
        when(personRepository.save(any(Person.class))).thenAnswer(i -> i.getArgument(0));
        InstitutionDTO institutionDto = new InstitutionDTO();
        institutionDto.setId(30L);
        // rank 1: the name is taken; rank 2: the name is free but the slug is taken; rank 3: both are free
        when(institutionRepository.existsByNameIgnoreCase("Organisation personnelle de A B")).thenReturn(true);
        when(institutionService.findByIdentifier("perso-a-b-2")).thenReturn(Optional.of(new InstitutionDTO()));
        when(institutionService.createInstitution(any(InstitutionDTO.class), any())).thenReturn(institutionDto);
        when(institutionRepository.findById(Long.valueOf(30L))).thenReturn(Optional.of(new Institution()));

        service.register(TOKEN, new PersonDTO(), "pw");

        ArgumentCaptor<InstitutionDTO> created = ArgumentCaptor.forClass(InstitutionDTO.class);
        verify(institutionService).createInstitution(created.capture(), any());
        assertEquals("Organisation personnelle de A B (3)", created.getValue().getName());
        assertEquals("perso-a-b-3", created.getValue().getIdentifier());
    }
}
