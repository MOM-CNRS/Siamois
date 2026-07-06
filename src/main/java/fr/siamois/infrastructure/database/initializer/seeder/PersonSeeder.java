package fr.siamois.infrastructure.database.initializer.seeder;


import fr.siamois.domain.models.auth.Person;
import fr.siamois.infrastructure.database.repositories.person.PersonRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class PersonSeeder {
    private final PersonRepository personRepository;

    public record PersonSpec(String email, String name, String lastname, String username) {
    }

    public Person findPersonOrReturnNull(String email) {
        return personRepository
                .findByEmailIgnoreCase(email)
                .orElse(null);
    }

    public Person findPersonOrThrow(String email) {
        Person p = findPersonOrReturnNull(email);
        if(p == null ) {
            throw new IllegalStateException("Person "+email+" introuvable");
        }
        return p;
    }

    public Person findOrCreatePerson(String nameLastName) {
        if (nameLastName == null || nameLastName.isBlank()) {
            throw new IllegalArgumentException("Nom/Prénom incorrect: " + nameLastName);
        }
        String trimmed = nameLastName.trim();
        int lastSpace = trimmed.lastIndexOf(' ');
        String firstname;
        String lastname;
        if (lastSpace < 1) {
            throw new IllegalArgumentException("Nom/Prénom incorrect: " + nameLastName);
        } else {
            firstname = trimmed.substring(0, lastSpace).trim();
            lastname = trimmed.substring(lastSpace + 1).trim();
        }
        String username = firstname.replaceAll("\\s+", "") + "." + lastname;
        return getOrCreatePerson(null, firstname, lastname, username);
    }

    private Person getOrCreatePerson(String email, String name, String lastname, String username) {
        Person authorGetOrCreated = personRepository
                .findByNameIgnoreCaseAndLastnameIgnoreCase(name, lastname)
                .orElse(null);
        if(authorGetOrCreated != null) {
            return authorGetOrCreated;
        }
        else {
            authorGetOrCreated = new Person();
            authorGetOrCreated.setEmail(email);
            authorGetOrCreated.setUsername(username);
            authorGetOrCreated.setName(name);
            authorGetOrCreated.setLastname(lastname);
            authorGetOrCreated.setPassword("mysuperstrongpassword");
            personRepository.save(authorGetOrCreated);
        }
        return authorGetOrCreated;
    }

    /**
     * Bulk-prefetches persons by the distinct firstnames appearing in {@code nameLastNameStrings}
     * (each a "Firstname Lastname" string as accepted by {@link #findOrCreatePerson}), for use with
     * {@link #resolveCached}. One query instead of one per row — callers still fall back to the
     * normal single-row get-or-create on a cache miss, so correctness is unaffected, only redundant
     * repeated lookups (e.g. the same excavator named on hundreds of rows) are eliminated.
     *
     * @param nameLastNameStrings "Firstname Lastname" strings to prefetch persons for
     * @return persons found, keyed by {@code cacheKey(firstname, lastname)}; entries with no match are omitted
     */
    public Map<String, Person> prefetchByNameLastName(Collection<String> nameLastNameStrings) {
        Set<String> firstnames = new HashSet<>();
        for (String n : nameLastNameStrings) {
            String trimmed = n == null ? null : n.trim();
            if (trimmed == null || trimmed.isBlank()) continue;
            int lastSpace = trimmed.lastIndexOf(' ');
            if (lastSpace >= 1) firstnames.add(trimmed.substring(0, lastSpace).trim());
        }
        Map<String, Person> cache = new HashMap<>();
        if (firstnames.isEmpty()) return cache;
        for (Person p : personRepository.findAllByNameIgnoreCaseIn(firstnames)) {
            if (p.getName() != null && p.getLastname() != null) {
                cache.put(cacheKey(p.getName(), p.getLastname()), p);
            }
        }
        return cache;
    }

    /**
     * Resolves a "Firstname Lastname" string against a cache built by {@link #prefetchByNameLastName},
     * falling back to {@link #findOrCreatePerson} (and populating the cache) on a miss.
     *
     * @param cache cache built by {@link #prefetchByNameLastName}, mutated in place on a cache miss
     * @param nameLastName "Firstname Lastname" string to resolve
     * @return the resolved (or newly created) {@link Person}
     */
    public Person resolveCached(Map<String, Person> cache, String nameLastName) {
        String key = normalizeKey(nameLastName);
        if (key != null) {
            Person cached = cache.get(key);
            if (cached != null) return cached;
        }
        Person resolved = findOrCreatePerson(nameLastName);
        if (key != null) cache.put(key, resolved);
        return resolved;
    }

    private String normalizeKey(String nameLastName) {
        if (nameLastName == null || nameLastName.isBlank()) return null;
        String trimmed = nameLastName.trim();
        int lastSpace = trimmed.lastIndexOf(' ');
        if (lastSpace < 1) return null;
        return cacheKey(trimmed.substring(0, lastSpace).trim(), trimmed.substring(lastSpace + 1).trim());
    }

    private String cacheKey(String name, String lastname) {
        return name.toLowerCase() + "|" + lastname.toLowerCase();
    }

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
