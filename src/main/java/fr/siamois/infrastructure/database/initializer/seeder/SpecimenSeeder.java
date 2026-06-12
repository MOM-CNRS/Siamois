package fr.siamois.infrastructure.database.initializer.seeder;

import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.institution.Institution;
import fr.siamois.domain.models.recordingunit.RecordingUnit;
import fr.siamois.domain.models.specimen.Specimen;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.infrastructure.database.repositories.institution.InstitutionRepository;
import fr.siamois.infrastructure.database.repositories.specimen.SpecimenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class SpecimenSeeder {

    private final ConceptSeeder conceptSeeder;
    private final InstitutionRepository institutionRepository;
    private final RecordingUnitSeeder recordingUnitSeeder;
    private final SpecimenRepository specimenRepository;
    private final PersonSeeder personSeeder;
    private final InstitutionSeeder institutionSeeder;

    public record SpecimenSpecs(String fullIdentifier, Integer identifier,
                                     ConceptSeeder.ConceptKey type,
                                     ConceptSeeder.ConceptKey category,
                                     ConceptSeeder.ConceptKey interpretation,
                                     String authorEmail,
                                     String institutionIdentifier,
                                     List<String> authors,
                                     List<String> collectors,
                                     OffsetDateTime creationTime,
                                     RecordingUnitSeeder.RecordingUnitKey recordingUnitKey) {

    }

    private List<Person> buildPersonList(List<String> emails, String fieldPrefix) {
        List<Person> persons = new ArrayList<>();
        if (emails == null) return persons;
        for (var email : emails) {
            persons.add(SeederUtils.field(fieldPrefix + "[" + email + "]", () -> personSeeder.findOrCreatePerson(email)));
        }
        return persons;
    }

    private void getOrCreateSpecimen(Specimen specimen) {

        Optional<Specimen> opt = specimenRepository.findByFullIdentifierAndInstitutionIdAndRecordingUnitFullIdentifierAndActionUnitFullIdentifier(
                specimen.getFullIdentifier(),
                specimen.getCreatedByInstitution().getId(),
                specimen.getRecordingUnit().getFullIdentifier(),
                specimen.getRecordingUnit().getActionUnit().getFullIdentifier());
        if (opt.isEmpty()) {
            specimenRepository.save(specimen);
        }
    }


    public void seed(List<SpecimenSpecs> specs, Long institutionId) {

        for (int i = 0; i < specs.size(); i++) {
            var s = specs.get(i);
            try {
                Concept cat      = SeederUtils.field("category",              () -> conceptSeeder.findConceptOrThrow(s.category));
                Person author    = SeederUtils.field("authorEmail",           () -> personSeeder.findOrCreatePerson(s.authorEmail));
                Institution institution = SeederUtils.field("institutionIdentifier", () -> {
                    Institution inst = institutionSeeder.findInstitutionOrReturnNull(s.institutionIdentifier);
                    if (inst == null) throw new IllegalStateException("Institution introuvable");
                    return inst;
                });

                List<Person> authors    = buildPersonList(s.authors,    "authors");
                List<Person> collectors = buildPersonList(s.collectors, "collectors");

                RecordingUnit ru = SeederUtils.field("UE", () -> recordingUnitSeeder.getRecordingUnitFromKey(s.recordingUnitKey, institutionId));

                Specimen toGetOrCreate = new Specimen();
                toGetOrCreate.setCreatedByInstitution(institution);
                toGetOrCreate.setIdentifier(s.identifier);
                toGetOrCreate.setCategory(cat);
                toGetOrCreate.setCreatedBy(author);
                toGetOrCreate.setFullIdentifier(s.fullIdentifier);
                toGetOrCreate.setRecordingUnit(ru);
                toGetOrCreate.setAuthors(authors);
                toGetOrCreate.setCollectors(collectors);
                toGetOrCreate.setCreationTime(s.creationTime);
                getOrCreateSpecimen(toGetOrCreate);
            } catch (Exception e) {
                throw new IllegalStateException(
                        "[Spécimen ligne " + (i + 1) + "] '" + s.fullIdentifier() + "' : " + e.getMessage(), e);
            }
        }
    }
}

