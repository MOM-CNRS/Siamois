package fr.siamois.infrastructure.database.initializer.seeder;


import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.institution.Institution;
import fr.siamois.domain.models.phase.Phase;
import fr.siamois.domain.models.recordingunit.RecordingUnit;
import fr.siamois.domain.models.spatialunit.SpatialUnit;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.infrastructure.database.repositories.PhaseRepository;
import fr.siamois.infrastructure.database.repositories.SpatialUnitRepository;
import fr.siamois.infrastructure.database.repositories.actionunit.ActionUnitRepository;
import fr.siamois.infrastructure.database.repositories.recordingunit.RecordingUnitRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.*;


@Service
@RequiredArgsConstructor
public class RecordingUnitSeeder {

    private final InstitutionSeeder institutionSeeder;
    private final ConceptSeeder conceptSeeder;
    private final RecordingUnitRepository recordingUnitRepository;
    private final SpatialUnitRepository spatialUnitRepository;
    private final ActionUnitRepository actionUnitRepository;
    private final PersonSeeder personSeeder;
    private final PhaseRepository phaseRepository;

    public record RecordingUnitSpecs(String fullIdentifier, Integer identifier,
                                     ConceptSeeder.ConceptKey type,
                                     ConceptSeeder.ConceptKey geomorphologicalCycle,
                                     ConceptSeeder.ConceptKey geomorphologicalAgent,
                                     ConceptSeeder.ConceptKey interpretation,
                                     String authorEmail,
                                     String institutionIdentifier,
                                     String author,
                                     String createdBy,
                                     List<String> excavators,
                                     OffsetDateTime creationTime,
                                     OffsetDateTime beginDate,
                                     OffsetDateTime endDate,
                                     SpatialUnitSeeder.SpatialUnitKey spatialUnitName,
                                     ActionUnitSeeder.ActionUnitKey actionUnitIdentifier,
                                     String description,
                                     String matrixColor,
                                     String matrixComposition,
                                     String matrixTexture,
                                     List<String> phaseIdentifiers) {

    }

    public record RecordingUnitKey(String fullIdentifier, String actionIdentifier) {
    }

    private void getOrCreateRecordingUnit(RecordingUnit recordingUnit) {

        Optional<RecordingUnit> opt = recordingUnitRepository.findByFullIdentifierAndInstitutionIdAndActionUnitFullIdentifier(
                recordingUnit.getFullIdentifier(),
                recordingUnit.getCreatedByInstitution().getId(),
                recordingUnit.getActionUnit().getFullIdentifier());
        if (opt.isEmpty()) {
            recordingUnitRepository.save(recordingUnit);
        }
    }


    public ActionUnit getActionUnitFromKey(ActionUnitSeeder.ActionUnitKey key) {
        return actionUnitRepository.findByIdentifierAndCreatedByInstitutionIdentifier(key.fullIdentifier(), key.institutionIdentifier())
                .orElseThrow(() -> new IllegalStateException("Action introuvable"));
    }

    public SpatialUnit getSpatialUnitFromKey(SpatialUnitSeeder.SpatialUnitKey key, Institution i) {
        return spatialUnitRepository.findByNameAndInstitution(key.unitName(), i.getId())
                .orElseThrow(() -> new IllegalStateException("Lieu "+key.unitName()+" introuvable"));
    }

    public RecordingUnit getRecordingUnitFromKey(RecordingUnitKey key, Long institutionId) {
        return recordingUnitRepository.findByFullIdentifierAndInstitutionIdAndActionUnitFullIdentifier(
                key.fullIdentifier, institutionId, key.actionIdentifier())
                .orElseThrow(() -> new IllegalStateException("Recording unit introuvable"));
    }

    public void seed(List<RecordingUnitSpecs> specs) {

        for (int i = 0; i < specs.size(); i++) {
            var s = specs.get(i);
            try {
            Concept type           = SeederUtils.field("type",                   () -> conceptSeeder.findConceptOrThrow(s.type));
            Concept geoCycle       = s.geomorphologicalCycle  != null ? SeederUtils.field("geomorphologicalCycle",  () -> conceptSeeder.findConceptOrThrow(s.geomorphologicalCycle))  : null;
            Concept geoAgent       = s.geomorphologicalAgent  != null ? SeederUtils.field("geomorphologicalAgent",  () -> conceptSeeder.findConceptOrThrow(s.geomorphologicalAgent))  : null;
            Concept interpretation = s.interpretation         != null ? SeederUtils.field("interpretation",         () -> conceptSeeder.findConceptOrThrow(s.interpretation))         : null;

            Institution institution = SeederUtils.field("institutionIdentifier", () -> {
                Institution inst = institutionSeeder.findInstitutionOrReturnNull(s.institutionIdentifier);
                if (inst == null) throw new IllegalStateException("Institution introuvable");
                return inst;
            });

            Person authorPerson = SeederUtils.field("author",    () -> personSeeder.findOrCreatePerson(s.author));
            Person createdBy    = SeederUtils.field("createdBy", () -> personSeeder.findOrCreatePerson(s.createdBy));

            List<Person> contributors = new ArrayList<>();
            if (s.excavators != null) {
                for (var email : s.excavators) {
                    contributors.add(SeederUtils.field("excavators[" + email + "]", () -> personSeeder.findOrCreatePerson(email)));
                }
            }

            SpatialUnit su = null;
            if(s.spatialUnitName != null) {
                su = SeederUtils.field("spatialUnitName", () -> getSpatialUnitFromKey(s.spatialUnitName, institution));
            }
            ActionUnit au = SeederUtils.field("actionUnitIdentifier", () -> getActionUnitFromKey(s.actionUnitIdentifier));

            RecordingUnit toGetOrCreate = new RecordingUnit();
            toGetOrCreate.setCreatedByInstitution(institution);
            toGetOrCreate.setDescription(s.description);
            toGetOrCreate.setMatrixTexture(s.matrixTexture);
            toGetOrCreate.setMatrixComposition(s.matrixComposition);
            toGetOrCreate.setMatrixColor(s.matrixColor);
            toGetOrCreate.setIdentifier(s.identifier);
            toGetOrCreate.setFullIdentifier(s.fullIdentifier);
            toGetOrCreate.setType(type);
            toGetOrCreate.setGeomorphologicalAgent(geoAgent);
            toGetOrCreate.setGeomorphologicalCycle(geoCycle);
            toGetOrCreate.setNormalizedInterpretation(interpretation);
            toGetOrCreate.setOpeningDate(s.beginDate);
            toGetOrCreate.setAuthor(authorPerson);
            toGetOrCreate.setContributors(contributors);
            toGetOrCreate.setCreatedBy(createdBy);
            toGetOrCreate.setClosingDate(s.endDate);
            toGetOrCreate.setCreationTime(s.creationTime);
            toGetOrCreate.setClosingDate(s.endDate);
            toGetOrCreate.setActionUnit(au);
            toGetOrCreate.setSpatialUnit(su);

            if (s.phaseIdentifiers != null && !s.phaseIdentifiers.isEmpty()) {
                Set<Phase> phases = new HashSet<>();
                for (String phaseId : s.phaseIdentifiers) {
                    phaseRepository.findByIdentifierAndActionUnitId(phaseId, au.getId())
                            .ifPresent(phases::add);
                }
                toGetOrCreate.setPhases(phases);
            }

            getOrCreateRecordingUnit(toGetOrCreate);

            } catch (Exception e) {
                throw new IllegalStateException(
                        "[UE ligne " + (i + 1) + "] '" + s.fullIdentifier() + "' : " + e.getMessage(), e);
            }
        }
    }
}
