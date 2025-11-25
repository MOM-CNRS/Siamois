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

    private void getOrCreateSpecimen(Specimen specimen) {

        Optional<Specimen> opt = specimenRepository.findByFullIdentifier(specimen.getFullIdentifier());
        if (opt.isEmpty()) {
            specimenRepository.save(specimen);
        }
    }


    public void seed(List<SpecimenSpecs> specs) {

        for (var s : specs) {
            // Find Type
            Concept type = conceptSeeder.findConceptOrThrow(s.type);
            Concept cat = conceptSeeder.findConceptOrThrow(s.category);
            // Find author
            Person author = personSeeder.findPersonOrThrow(s.authorEmail);

            // Find Institution
            Institution institution = institutionSeeder.findInstitutionOrReturnNull(s.institutionIdentifier);
            if(institution == null ) {
                throw new IllegalStateException("Institution introuvable");
            }

            List<Person> authors = new ArrayList<>();
            List<Person> collectors = new ArrayList<>();
            if (s.authors != null) {
                for (var email : s.authors) {
                    Person p = personSeeder.findPersonOrThrow(email);
                    authors.add(p);
                }
            }
            if (s.collectors != null) {
                for (var email : s.collectors) {
                    Person p = personSeeder.findPersonOrThrow(email);
                    collectors.add(p);
                }
            }

            RecordingUnit ru = recordingUnitSeeder.getRecordingUnitFromKey(s.recordingUnitKey);

            Specimen toGetOrCreate = new Specimen();
            toGetOrCreate.setCreatedByInstitution(institution);
            toGetOrCreate.setIdentifier(s.identifier);
            toGetOrCreate.setCategory(cat);
            toGetOrCreate.setCreatedBy(author);
            toGetOrCreate.setFullIdentifier(s.fullIdentifier);
            toGetOrCreate.setType(type);
            toGetOrCreate.setRecordingUnit(ru);
            toGetOrCreate.setAuthors(authors);
            toGetOrCreate.setCollectors(collectors);
            toGetOrCreate.setCreationTime(s.creationTime);
            getOrCreateSpecimen(toGetOrCreate);

        }
    }
}

