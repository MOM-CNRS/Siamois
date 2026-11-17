package fr.siamois.infrastructure.database.initializer.seeder;

import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.institution.Institution;
import fr.siamois.domain.services.permissions.PersonProfileAssignmentService;
import fr.siamois.infrastructure.database.repositories.person.PersonRepository;
import fr.siamois.infrastructure.dataimport.ImportSchema;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor
public class PersonSeeder {
    private final PersonRepository personRepository;
    private final PersonProfileAssignmentService personProfileAssignmentService;

    public record PersonSpec(String email, String name, String lastname, String username) {
    }

    /**
     * Finds a person by their email address (case-insensitive) or returns null if not found.
     *
     * @param email the email address
     * @return the Person found, or null
     */
    public Person findPersonOrReturnNull(String email) {
        return personRepository
                .findByEmailIgnoreCase(email)
                .orElse(null);
    }

    /**
     * Finds a person by their email address or throws an {@link IllegalStateException}.
     *
     * @param email the email address
     * @return the Person found
     */
    public Person findPersonOrThrow(String email) {
        Person p = findPersonOrReturnNull(email);
        if (p == null) {
            throw new IllegalStateException("Person " + email + " introuvable");
        }
        return p;
    }

    /**
     * Finds or creates a person from a "Firstname Lastname" or email string.
     *
     * @param nameLastName "Firstname Lastname" or email string
     * @return the found or newly created {@link Person}
     */
    public Person findOrCreatePerson(String nameLastName) {
        return findOrCreatePerson(nameLastName, null);
    }

    /**
     * Finds or creates a person from a "Firstname Lastname" or email string,
     * and ensures they are added as an organization member of the given institution if specified.
     *
     * @param nameLastName "Firstname Lastname" or email string
     * @param institution the institution to attach the person to, or null
     * @return the found or newly created {@link Person}
     */
    public Person findOrCreatePerson(String nameLastName, Institution institution) {
        if (nameLastName == null || nameLastName.isBlank()) {
            throw new IllegalArgumentException("Nom/Prénom incorrect: " + nameLastName);
        }
        String trimmed = nameLastName.trim();
        Person resolved;
        if (trimmed.contains("@")) {
            resolved = findOrCreatePersonFromEmail(trimmed);
        } else {
            resolved = findOrCreatePersonFromName(trimmed);
        }
        if (institution != null) {
            ensureMemberOfInstitution(resolved, institution);
        }
        return resolved;
    }

    private Person findOrCreatePersonFromEmail(String email) {
        Person existing = findPersonOrReturnNull(email);
        if (existing != null) {
            return existing;
        }
        String localPart = email.substring(0, email.indexOf('@'));
        String firstname;
        String lastname;
        int dot = localPart.indexOf('.');
        if (dot >= 1 && dot < localPart.length() - 1) {
            firstname = capitalize(localPart.substring(0, dot));
            lastname = capitalize(localPart.substring(dot + 1));
        } else {
            firstname = localPart;
            lastname = localPart;
        }
        String username = localPart;
        return getOrCreatePerson(email, firstname, lastname, username);
    }

    private Person findOrCreatePersonFromName(String nameLastName) {
        int lastSpace = nameLastName.lastIndexOf(' ');
        if (lastSpace < 1) {
            throw new IllegalArgumentException("Nom/Prénom incorrect: " + nameLastName);
        }
        String firstname = nameLastName.substring(0, lastSpace).trim();
        String lastname = nameLastName.substring(lastSpace + 1).trim();
        String username = firstname.replaceAll("\\s+", "") + "." + lastname;
        return getOrCreatePerson(null, firstname, lastname, username);
    }

    private static String capitalize(String str) {
        if (str == null || str.isEmpty()) return str;
        return Character.toUpperCase(str.charAt(0)) + str.substring(1);
    }

    private Person getOrCreatePerson(String email, String name, String lastname, String username) {
        if (email != null && !email.isBlank()) {
            Person byEmail = personRepository.findByEmailIgnoreCase(email).orElse(null);
            if (byEmail != null) {
                return byEmail;
            }
        }
        Person authorGetOrCreated = personRepository
                .findByNameIgnoreCaseAndLastnameIgnoreCase(name, lastname)
                .orElse(null);
        if (authorGetOrCreated != null) {
            return authorGetOrCreated;
        } else {
            authorGetOrCreated = new Person();
            authorGetOrCreated.setEmail(email);
            authorGetOrCreated.setUsername(determineAvailableUsername(username));
            authorGetOrCreated.setName(name);
            authorGetOrCreated.setLastname(lastname);
            authorGetOrCreated.setPassword("mysuperstrongpassword");
            Person saved = personRepository.save(authorGetOrCreated);
            if (saved != null) {
                authorGetOrCreated = saved;
            }
        }
        return authorGetOrCreated;
    }

    private String determineAvailableUsername(String baseUsername) {
        String candidate = baseUsername;
        if (candidate.length() > Person.USERNAME_MAX_LENGTH) {
            candidate = candidate.substring(0, Person.USERNAME_MAX_LENGTH);
        }
        if (personRepository.findByUsernameIgnoreCase(candidate).isEmpty()) {
            return candidate;
        }
        for (int suffix = 1; suffix < 1000; suffix++) {
            String suffixStr = String.valueOf(suffix);
            int maxBaseLen = Person.USERNAME_MAX_LENGTH - suffixStr.length();
            String prefix = candidate.length() > maxBaseLen ? candidate.substring(0, maxBaseLen) : candidate;
            String attempt = prefix + suffixStr;
            if (personRepository.findByUsernameIgnoreCase(attempt).isEmpty()) {
                return attempt;
            }
        }
        return candidate;
    }

    /**
     * Ensures that the person is assigned the organization member profile of the institution,
     * unless the person represents a system account.
     *
     * @param person the person to add
     * @param institution the institution
     */
    public void ensureMemberOfInstitution(Person person, Institution institution) {
        if (person == null || institution == null || person.getId() == null || institution.getId() == null) {
            return;
        }
        if (isSystemPerson(person)) {
            return;
        }
        personProfileAssignmentService.assignOrganizationMemberProfile(institution, person);
    }

    private boolean isSystemPerson(Person person) {
        if (person == null) return true;
        if (ImportSchema.SIAMOIS_SYSTEM.equalsIgnoreCase(person.getUsername())) return true;
        if ("siamois.system".equalsIgnoreCase(person.getUsername())) return true;
        if ("siamois".equalsIgnoreCase(person.getName()) && "system".equalsIgnoreCase(person.getLastname())) return true;
        return person.getEmail() != null && ImportSchema.SIAMOIS_SYSTEM.equalsIgnoreCase(person.getEmail());
    }

    /**
     * Bulk-prefetches persons by the distinct firstnames appearing in {@code nameLastNameStrings}
     * (each a "Firstname Lastname" or email string as accepted by {@link #findOrCreatePerson}), for use with
     * {@link #resolveCached}. One query instead of one per row — callers still fall back to the
     * normal single-row get-or-create on a cache miss, so correctness is unaffected, only redundant
     * repeated lookups (e.g. the same excavator named on hundreds of rows) are eliminated.
     *
     * @param nameLastNameStrings "Firstname Lastname" or email strings to prefetch persons for
     * @return persons found, keyed by {@code cacheKey(firstname, lastname)} or lowercase email
     */
    public Map<String, Person> prefetchByNameLastName(Collection<String> nameLastNameStrings) {
        Set<String> firstnames = new HashSet<>();
        Set<String> emails = new HashSet<>();
        for (String n : nameLastNameStrings) {
            String trimmed = n == null ? null : n.trim();
            if (trimmed == null || trimmed.isBlank()) continue;
            if (trimmed.contains("@")) {
                emails.add(trimmed);
            } else {
                int lastSpace = trimmed.lastIndexOf(' ');
                if (lastSpace >= 1) firstnames.add(trimmed.substring(0, lastSpace).trim());
            }
        }
        Map<String, Person> cache = new HashMap<>();
        if (!firstnames.isEmpty()) {
            for (Person p : personRepository.findAllByNameIgnoreCaseIn(firstnames)) {
                if (p.getName() != null && p.getLastname() != null) {
                    cache.put(cacheKey(p.getName(), p.getLastname()), p);
                }
            }
        }
        for (String email : emails) {
            personRepository.findByEmailIgnoreCase(email).ifPresent(p -> cache.put(email.toLowerCase(), p));
        }
        return cache;
    }

    /**
     * Resolves a "Firstname Lastname" or email string against a cache built by {@link #prefetchByNameLastName},
     * falling back to {@link #findOrCreatePerson} (and populating the cache) on a miss.
     *
     * @param cache cache built by {@link #prefetchByNameLastName}, mutated in place on a cache miss
     * @param nameLastName "Firstname Lastname" or email string to resolve
     * @return the resolved (or newly created) {@link Person}
     */
    public Person resolveCached(Map<String, Person> cache, String nameLastName) {
        return resolveCached(cache, nameLastName, null);
    }

    /**
     * Resolves a "Firstname Lastname" or email string against a cache built by {@link #prefetchByNameLastName},
     * falling back to {@link #findOrCreatePerson} (and populating the cache) on a miss.
     * If an institution is provided and the resolved person is not a system person, ensures that the person
     * is added as an organization member of the institution.
     *
     * @param cache cache built by {@link #prefetchByNameLastName}, mutated in place on a cache miss
     * @param nameLastName "Firstname Lastname" or email string to resolve
     * @param institution institution to attach the person to, or null
     * @return the resolved (or newly created) {@link Person}
     */
    public Person resolveCached(Map<String, Person> cache, String nameLastName, Institution institution) {
        String key = normalizeKey(nameLastName);
        if (key != null && institution != null && institution.getId() != null) {
            String assignedKey = key + "#inst:" + institution.getId();
            Person cached = cache.get(assignedKey);
            if (cached != null) return cached;
        }
        if (key != null) {
            Person cached = cache.get(key);
            if (cached != null) {
                if (institution != null) {
                    ensureMemberOfInstitution(cached, institution);
                    if (institution.getId() != null) {
                        cache.put(key + "#inst:" + institution.getId(), cached);
                    }
                }
                return cached;
            }
        }
        Person resolved = findOrCreatePerson(nameLastName, institution);
        if (key != null) {
            cache.put(key, resolved);
            if (institution != null && institution.getId() != null) {
                cache.put(key + "#inst:" + institution.getId(), resolved);
            }
        }
        return resolved;
    }

    private String normalizeKey(String nameLastName) {
        if (nameLastName == null || nameLastName.isBlank()) return null;
        String trimmed = nameLastName.trim();
        if (trimmed.contains("@")) return trimmed.toLowerCase();
        int lastSpace = trimmed.lastIndexOf(' ');
        if (lastSpace < 1) return null;
        return cacheKey(trimmed.substring(0, lastSpace).trim(), trimmed.substring(lastSpace + 1).trim());
    }

    private String cacheKey(String name, String lastname) {
        return name.toLowerCase() + "|" + lastname.toLowerCase();
    }

    /**
     * Seeds persons from a list of specs.
     *
     * @param specs the person specs to insert or retrieve
     * @return map of email to Person
     */
    public Map<String, Person> seed(List<PersonSpec> specs) {
        Map<String, Person> result = new HashMap<>();
        for (int i = 0; i < specs.size(); i++) {
            var s = specs.get(i);
            try {
                result.put(s.email, getOrCreatePerson(s.email, s.name, s.lastname, s.username));
            } catch (Exception e) {
                throw new IllegalStateException(
                        "[Personne ligne " + (i + 1) + "] '" + s.email + "' : " + e.getMessage(), e);
            }
        }
        return result;
    }
}
