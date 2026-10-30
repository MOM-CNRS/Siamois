package fr.siamois.domain.services.auth;

import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.auth.invitation.InvitationLink;
import fr.siamois.domain.models.auth.invitation.InvitationLinkStatus;
import fr.siamois.domain.models.exceptions.auth.*;
import fr.siamois.domain.models.exceptions.institution.FailedInstitutionSaveException;
import fr.siamois.domain.models.exceptions.institution.InstitutionAlreadyExistException;
import fr.siamois.domain.models.exceptions.api.InvalidEndpointException;
import fr.siamois.domain.models.exceptions.api.NotSiamoisThesaurusException;
import fr.siamois.domain.models.institution.Institution;
import fr.siamois.domain.services.InstitutionService;
import fr.siamois.domain.services.person.PersonService;
import fr.siamois.domain.services.vocabulary.SiamoisThesaurus;
import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.infrastructure.database.repositories.institution.InstitutionRepository;
import fr.siamois.infrastructure.database.repositories.person.InvitationLinkRepository;
import fr.siamois.infrastructure.database.repositories.person.PersonRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.text.Normalizer;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;

/**
 * Invitation links: a super admin creates a link that lets several people sign up on their own, until it
 * expires or its quota of accounts is reached. Each account created gets its own personal organization.
 * The accounts are not e-mail verified.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InvitationLinkService {

    public static final Duration DEFAULT_VALIDITY = Duration.ofHours(24);
    /** Slightly under the one month cap enforced by the database, whatever the length of the month. */
    public static final Duration MAX_VALIDITY = Duration.ofDays(30);
    public static final String PERSONAL_ORGANIZATION_PREFIX = "Organisation personnelle de ";
    public static final String PERSONAL_ORGANIZATION_IDENTIFIER_PREFIX = "perso-";

    private static final int MAX_SLUG_LENGTH = 60;
    private static final int TOKEN_LENGTH = 40;
    private static final String ALLOWED_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";

    private final InvitationLinkRepository invitationLinkRepository;
    private final PersonRepository personRepository;
    private final PersonService personService;
    private final InstitutionService institutionService;
    private final InstitutionRepository institutionRepository;
    private final SiamoisThesaurus siamoisThesaurus;
    private final HttpServletRequest httpServletRequest;
    private final SecureRandom random = new SecureRandom();

    /** A link with the number of accounts created through it and its computed status. */
    public record Summary(InvitationLink link, long createdAccounts, InvitationLinkStatus status, String createdByName) {
    }

    /** A freshly created link. The raw token is only known now: only its hash is stored. */
    public record Created(InvitationLink link, String rawToken) {
    }

    /**
     * Creates an invitation link.
     *
     * @param validity    how long the link stays usable, at most {@link #MAX_VALIDITY}
     * @param maxAccounts how many accounts can be created, between 1 and {@link InvitationLink#MAX_ACCOUNTS_LIMIT}
     * @param createdById the id of the admin creating the link
     * @throws IllegalArgumentException if the validity or the quota is out of bounds
     */
    @Transactional
    public Created create(Duration validity, int maxAccounts, Long createdById) {
        if (validity == null || validity.isNegative() || validity.isZero() || validity.compareTo(MAX_VALIDITY) > 0) {
            throw new IllegalArgumentException("The validity must be positive and at most " + MAX_VALIDITY.toDays() + " days");
        }
        if (maxAccounts < 1 || maxAccounts > InvitationLink.MAX_ACCOUNTS_LIMIT) {
            throw new IllegalArgumentException("The number of accounts must be between 1 and " + InvitationLink.MAX_ACCOUNTS_LIMIT);
        }

        String rawToken = generateToken();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        InvitationLink link = new InvitationLink();
        link.setTokenHash(hash(rawToken));
        link.setMaxAccounts(maxAccounts);
        link.setCreationDate(now);
        link.setExpirationDate(now.plus(validity));
        link.setCreatedBy(personRepository.findById(createdById)
                .orElseThrow(() -> new EntityNotFoundException("Person not found")));
        return new Created(invitationLinkRepository.save(link), rawToken);
    }

    /** Every link, most recent first, with its number of created accounts and status. */
    @Transactional(readOnly = true)
    public List<Summary> findAll() {
        List<InvitationLink> links = invitationLinkRepository.findAllByOrderByCreationDateDesc();
        if (links.isEmpty()) {
            return List.of();
        }
        Map<Long, Long> counts = new HashMap<>();
        for (Object[] row : personRepository.countByInvitationLinkIds(links.stream().map(InvitationLink::getId).toList())) {
            counts.put((Long) row[0], (Long) row[1]);
        }
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        return links.stream()
                .map(l -> {
                    long created = counts.getOrDefault(l.getId(), 0L);
                    Person author = l.getCreatedBy();
                    return new Summary(l, created, statusOf(l, created, now), author == null ? "" : author.displayName());
                })
                .toList();
    }

    /** Revokes a link. The accounts already created through it are left untouched. */
    @Transactional
    public void revoke(Long linkId) {
        InvitationLink link = invitationLinkRepository.findById(linkId)
                .orElseThrow(() -> new EntityNotFoundException("Invitation link not found"));
        link.setRevoked(true);
        invitationLinkRepository.save(link);
    }

    /** Tells whether a person can still sign up with the raw token. */
    @Transactional(readOnly = true)
    public boolean isUsable(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return false;
        }
        return invitationLinkRepository.findByTokenHash(hash(rawToken))
                .map(l -> statusOf(l, personRepository.countByInvitationLinkId(l.getId()), OffsetDateTime.now(ZoneOffset.UTC))
                        == InvitationLinkStatus.ACTIVE)
                .orElse(false);
    }

    /**
     * Creates the account of a person who followed the link, together with their personal organization, in a
     * single transaction: either everything is created and a place of the link is used, or nothing is. The
     * link row is locked while the quota is checked, so concurrent sign-ups can never exceed it.
     *
     * @param rawToken the secret token of the link
     * @param person   the person to create (first name, last name and e-mail: the username is generated)
     * @param password the plain password chosen by the person
     * @return the created person
     * @throws InvitationLinkUnavailableException if the link is unknown, revoked, expired or full
     */
    @Transactional(rollbackFor = Exception.class)
    public PersonDTO register(String rawToken, PersonDTO person, String password) throws InvitationLinkUnavailableException,
            InvalidUsernameException, InvalidEmailException, UserAlreadyExistException, InvalidPasswordException, InvalidNameException,
            InstitutionAlreadyExistException, FailedInstitutionSaveException {
        InvitationLink link = invitationLinkRepository.findByTokenHashForUpdate(hash(rawToken == null ? "" : rawToken))
                .orElseThrow(() -> new InvitationLinkUnavailableException("Unknown invitation link"));
        long created = personRepository.countByInvitationLinkId(link.getId());
        InvitationLinkStatus status = statusOf(link, created, OffsetDateTime.now(ZoneOffset.UTC));
        if (status != InvitationLinkStatus.ACTIVE) {
            throw new InvitationLinkUnavailableException("Invitation link is " + status);
        }

        person.setUsername(personService.generateUniqueUsername(person.getName(), person.getLastname(), person.getEmail(), null));
        PersonDTO createdPerson = personService.createPerson(person, password);
        Person entity = personRepository.findById(createdPerson.getId())
                .orElseThrow(() -> new EntityNotFoundException("Person not found"));
        entity.setMailVerified(false);
        entity.setInvitationLink(link);
        entity = personRepository.save(entity);

        createPersonalOrganization(createdPerson, entity);
        log.info("Account {} created through invitation link {}", entity.getUsername(), link.getId());
        return createdPerson;
    }

    private void createPersonalOrganization(PersonDTO personDto, Person person) throws InstitutionAlreadyExistException,
            FailedInstitutionSaveException {
        // Name and slug share the same rank, so "… de Ada Lovelace (2)" goes with "perso-ada-lovelace-2".
        int rank = 1;
        while (institutionRepository.existsByNameIgnoreCase(personalOrganizationName(person, rank))
                || institutionService.findByIdentifier(personalOrganizationIdentifier(person, rank)).isPresent()) {
            rank++;
        }
        InstitutionDTO dto = new InstitutionDTO();
        dto.setName(personalOrganizationName(person, rank));
        dto.setIdentifier(personalOrganizationIdentifier(person, rank));
        InstitutionDTO institutionDto;
        try {
            institutionDto = institutionService.createInstitution(dto, siamoisThesaurus.url());
        } catch (InvalidEndpointException | NotSiamoisThesaurusException e) {
            throw new FailedInstitutionSaveException("Unusable thesaurus " + siamoisThesaurus.url());
        }
        institutionService.addToManagers(institutionDto, personDto);
        Institution institution = institutionRepository.findById(institutionDto.getId())
                .orElseThrow(() -> new EntityNotFoundException("Institution not found"));
        institution.setPersonalOwner(person);
        institutionRepository.save(institution);
    }

    /** "Organisation personnelle de Ada Lovelace", then "… (2)", "… (3)" on a clash, within the name length limit. */
    static String personalOrganizationName(Person person, int rank) {
        String suffix = rank <= 1 ? "" : " (" + rank + ")";
        String base = PERSONAL_ORGANIZATION_PREFIX + person.getName() + " " + person.getLastname();
        int room = Institution.MAX_NAME_LENGTH - suffix.length();
        return (base.length() <= room ? base : base.substring(0, room).strip()) + suffix;
    }

    /** "perso-ada-lovelace", then "perso-ada-lovelace-2", "-3" on a clash. */
    static String personalOrganizationIdentifier(Person person, int rank) {
        String slug = slugify(person.getName() + " " + person.getLastname());
        if (slug.isEmpty()) {
            slug = "user";
        }
        if (slug.length() > MAX_SLUG_LENGTH) {
            slug = trimHyphens(slug.substring(0, MAX_SLUG_LENGTH));
        }
        return PERSONAL_ORGANIZATION_IDENTIFIER_PREFIX + slug + (rank <= 1 ? "" : "-" + rank);
    }

    /** Lower case ASCII letters and digits separated by single hyphens. */
    static String slugify(String text) {
        String ascii = Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return trimHyphens(ascii.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-"));
    }

    private static String trimHyphens(String text) {
        int start = 0;
        int end = text.length();
        while (start < end && text.charAt(start) == '-') {
            start++;
        }
        while (end > start && text.charAt(end - 1) == '-') {
            end--;
        }
        return text.substring(start, end);
    }

    /** The absolute link to share for a raw token. */
    public String link(String rawToken) {
        int port = httpServletRequest.getServerPort();
        String domain = httpServletRequest.getScheme() + "://" + httpServletRequest.getServerName()
                + (port != 80 && port != 443 ? ":" + port : "");
        return domain + httpServletRequest.getContextPath() + "/join/" + rawToken;
    }

    static InvitationLinkStatus statusOf(InvitationLink link, long createdAccounts, OffsetDateTime now) {
        if (link.isRevoked()) {
            return InvitationLinkStatus.REVOKED;
        }
        if (!link.getExpirationDate().isAfter(now)) {
            return InvitationLinkStatus.EXPIRED;
        }
        if (createdAccounts >= link.getMaxAccounts()) {
            return InvitationLinkStatus.FULL;
        }
        return InvitationLinkStatus.ACTIVE;
    }

    private String generateToken() {
        StringBuilder sb = new StringBuilder(TOKEN_LENGTH);
        for (int i = 0; i < TOKEN_LENGTH; i++) {
            sb.append(ALLOWED_CHARS.charAt(random.nextInt(ALLOWED_CHARS.length())));
        }
        return sb.toString();
    }

    static String hash(String rawToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
