package fr.siamois.infrastructure.database.initializer.seeder;


import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.exceptions.ErrorProcessingExpansionException;
import fr.siamois.domain.models.exceptions.api.NotSiamoisThesaurusException;
import fr.siamois.domain.models.exceptions.database.DatabaseDataInitException;
import fr.siamois.domain.models.institution.Institution;
import fr.siamois.domain.models.vocabulary.Vocabulary;
import fr.siamois.domain.services.vocabulary.FieldConfigurationService;
import fr.siamois.infrastructure.database.repositories.institution.InstitutionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;


@Service
@RequiredArgsConstructor
public class InstitutionSeeder {
    private final InstitutionRepository institutionRepository;
    private final FieldConfigurationService fieldConfigurationService;
    private final PersonSeeder personSeeder;
    private final ThesaurusSeeder thesaurusSeeder;

    public record InstitutionSpec(String name, String description, String identifier, List<String> managerEmails,
                                  String baseUri, String externalId) {
    }

    public Institution findInstitutionOrReturnNull(String identifier) {
        Optional<Institution> opt = institutionRepository.findInstitutionByIdentifier(identifier);
        return opt.orElse(null);
    }

    private void getOrCreateInstitution(Institution i, Vocabulary vocabulary) throws DatabaseDataInitException {
        Institution inst = findInstitutionOrReturnNull(i.getIdentifier());
        if (inst == null) {
            inst = institutionRepository.save(i);
        }
        try {
            fieldConfigurationService.setupFieldConfigurationForInstitution(inst, vocabulary);
        } catch (NotSiamoisThesaurusException | ErrorProcessingExpansionException e) {
            throw new DatabaseDataInitException("error with thesaurus init:",e);
        }
    }

    public void seed(List<InstitutionSpec> specs) throws DatabaseDataInitException {
        for (var s : specs) {
            Set<Person> managers = new HashSet<>();
            if (s.managerEmails != null) {
                for (var email : s.managerEmails) {
                    Person p = personSeeder.findPersonOrReturnNull(email);
                    if(p == null) {
                        throw new IllegalArgumentException("Invalid email: " + email);
                    }
                    managers.add(p);
                }
            }

            // find thesaurus
            Vocabulary thesaurus = thesaurusSeeder.findVocabularyOrReturnNull(s.baseUri, s.externalId);
            if(thesaurus == null) {
                throw new IllegalArgumentException("Invalid thesaurus: " + s.externalId);
            }

            Institution toCreate = new Institution();
            toCreate.setName(s.name);
            toCreate.setIdentifier(s.identifier);
            toCreate.setDescription(s.description);
            toCreate.setManagers(managers);
            getOrCreateInstitution(toCreate, thesaurus);
        }
    }
}
