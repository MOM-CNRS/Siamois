package fr.siamois.infrastructure.database.initializer.seeder;


import fr.siamois.domain.models.auth.Person;
import fr.siamois.infrastructure.database.repositories.person.PersonRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
