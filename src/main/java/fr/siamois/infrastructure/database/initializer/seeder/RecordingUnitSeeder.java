package fr.siamois.infrastructure.database.initializer.seeder;


import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.institution.Institution;
import fr.siamois.domain.models.recordingunit.RecordingUnit;
import fr.siamois.domain.models.spatialunit.SpatialUnit;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.infrastructure.database.repositories.SpatialUnitRepository;
import fr.siamois.infrastructure.database.repositories.actionunit.ActionUnitRepository;
import fr.siamois.infrastructure.database.repositories.recordingunit.RecordingUnitRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;


@Service
@RequiredArgsConstructor
public class RecordingUnitSeeder {

    private final InstitutionSeeder institutionSeeder;
    private final ConceptSeeder conceptSeeder;
    private final RecordingUnitRepository recordingUnitRepository;
    private final SpatialUnitRepository spatialUnitRepository;
    private final ActionUnitRepository actionUnitRepository;
    private final PersonSeeder personSeeder;

    public record RecordingUnitSpecs(String fullIdentifier, Integer identifier,
                                     ConceptSeeder.ConceptKey type,
                                     ConceptSeeder.ConceptKey secondaryType,
                                     ConceptSeeder.ConceptKey thirdType,
                                     String authorEmail,
                                     String institutionIdentifier,
                                     String author,
                                     String createdBy,
                                     List<String> excavators,
                                     OffsetDateTime creationTime,
                                     OffsetDateTime beginDate,
                                     OffsetDateTime endDate,
                                     SpatialUnitSeeder.SpatialUnitKey spatialUnitName,
                                     ActionUnitSeeder.ActionUnitKey actionUnitIdentifier) {

    }

    public record RecordingUnitKey(String fullIdentifier) {
    }

    private void getOrCreateRecordingUnit(RecordingUnit recordingUnit) {

        Optional<RecordingUnit> opt = recordingUnitRepository.findByFullIdentifier(recordingUnit.getFullIdentifier());
        if (opt.isEmpty()) {
            recordingUnitRepository.save(recordingUnit);
        }
    }


    public ActionUnit getActionUnitFromKey(ActionUnitSeeder.ActionUnitKey key) {
        return actionUnitRepository.findByFullIdentifier(key.fullIdentifier())
                .orElseThrow(() -> new IllegalStateException("Action introuvable"));
    }

    public SpatialUnit getSpatialUnitFromKey(SpatialUnitSeeder.SpatialUnitKey key, Institution i) {
        return spatialUnitRepository.findByNameAndInstitution(key.unitName(), i.getId())
                .orElseThrow(() -> new IllegalStateException("Spatial unit introuvable"));
    }

    public RecordingUnit getRecordingUnitFromKey(RecordingUnitKey key) {
        return recordingUnitRepository.findByFullIdentifier(key.fullIdentifier)
                .orElseThrow(() -> new IllegalStateException("Recording unit introuvable"));
    }

    public void seed(List<RecordingUnitSpecs> specs) {

        for (var s : specs) {
            // Find Type
            Concept type = conceptSeeder.findConceptOrThrow(s.type);
            Concept secondaryType = conceptSeeder.findConceptOrThrow(s.secondaryType);
            Concept thirdType = conceptSeeder.findConceptOrThrow(s.thirdType);

            // Find Institution
            Institution institution = institutionSeeder.findInstitutionOrReturnNull(s.institutionIdentifier);
            if(institution == null ) {
                throw new IllegalStateException("Institution introuvable");
            }

            Person authorPerson = personSeeder.findPersonOrThrow(s.author);
            Person createdBy = personSeeder.findPersonOrThrow(s.createdBy);

            List<Person> contributors = new ArrayList<>();

            if (s.excavators != null) {
                for (var email : s.excavators) {
                    Person p = personSeeder.findPersonOrThrow(email);
                    contributors.add(p);
                }
            }


            SpatialUnit su = getSpatialUnitFromKey(s.spatialUnitName, institution);
            ActionUnit au = getActionUnitFromKey(s.actionUnitIdentifier);

            RecordingUnit toGetOrCreate = new RecordingUnit();
            toGetOrCreate.setCreatedByInstitution(institution);
            toGetOrCreate.setIdentifier(s.identifier);
            toGetOrCreate.setFullIdentifier(s.fullIdentifier);
            toGetOrCreate.setType(type);
            toGetOrCreate.setSecondaryType(secondaryType);
            toGetOrCreate.setThirdType(thirdType);
            toGetOrCreate.setOpeningDate(s.beginDate);
            toGetOrCreate.setAuthor(authorPerson);
            toGetOrCreate.setContributors(contributors);
            toGetOrCreate.setCreatedBy(createdBy);
            toGetOrCreate.setClosingDate(s.endDate);
            toGetOrCreate.setCreationTime(s.creationTime);
            toGetOrCreate.setClosingDate(s.endDate);
            toGetOrCreate.setActionUnit(au);
            toGetOrCreate.setSpatialUnit(su);
            getOrCreateRecordingUnit(toGetOrCreate);

        }
    }
}
