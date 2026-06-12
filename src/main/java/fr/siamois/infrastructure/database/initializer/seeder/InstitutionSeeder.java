package fr.siamois.infrastructure.database.initializer.seeder;


import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.exceptions.ErrorProcessingExpansionException;
import fr.siamois.domain.models.exceptions.api.NotSiamoisThesaurusException;
import fr.siamois.domain.models.exceptions.database.DatabaseDataInitException;
import fr.siamois.domain.models.institution.Institution;
import fr.siamois.domain.models.vocabulary.Vocabulary;
import fr.siamois.domain.services.vocabulary.FieldConfigurationService;
import fr.siamois.infrastructure.database.repositories.institution.InstitutionRepository;
import fr.siamois.mapper.InstitutionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;


@Service
@RequiredArgsConstructor
public class InstitutionSeeder {
    private final InstitutionRepository institutionRepository;
    private final FieldConfigurationService fieldConfigurationService;
    private final PersonSeeder personSeeder;
    private final ThesaurusSeeder thesaurusSeeder;
    private final InstitutionMapper institutionMapper;

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
            fieldConfigurationService.setupFieldConfigurationForInstitution(
                    Objects.requireNonNull(institutionMapper.convert(inst))
                    , vocabulary);
        } catch (NotSiamoisThesaurusException | ErrorProcessingExpansionException e) {
            throw new DatabaseDataInitException("error with thesaurus init:",e);
        }
    }

    private Set<Person> buildManagers(List<String> managerEmails) {
        Set<Person> managers = new HashSet<>();
        if (managerEmails == null) return managers;
        for (var email : managerEmails) {
            managers.add(SeederUtils.field("managerEmails[" + email + "]", () -> {
                Person p = personSeeder.findPersonOrReturnNull(email);
                if (p == null) throw new IllegalArgumentException("Email introuvable: " + email);
                return p;
            }));
        }
        return managers;
    }

    public void seed(List<InstitutionSpec> specs) throws DatabaseDataInitException {
        for (int i = 0; i < specs.size(); i++) {
            var s = specs.get(i);
            try {
                Set<Person> managers = buildManagers(s.managerEmails);

                Vocabulary thesaurus = SeederUtils.field("thesaurus", () -> {
                    Vocabulary t = thesaurusSeeder.findVocabularyOrReturnNull(s.baseUri, s.externalId);
                    if (t == null) throw new IllegalArgumentException("Thésaurus introuvable: " + s.externalId);
                    return t;
                });

                Institution toCreate = new Institution();
                toCreate.setName(s.name);
                toCreate.setIdentifier(s.identifier);
                toCreate.setDescription(s.description);
                toCreate.setManagers(managers);
                getOrCreateInstitution(toCreate, thesaurus);
            } catch (Exception e) {
                throw new IllegalStateException(
                        "[Institution ligne " + (i + 1) + "] '" + s.identifier() + "' : " + e.getMessage(), e);
            }
        }
    }
}
