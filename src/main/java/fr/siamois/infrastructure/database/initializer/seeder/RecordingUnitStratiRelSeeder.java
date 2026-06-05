package fr.siamois.infrastructure.database.initializer.seeder;

import fr.siamois.domain.models.recordingunit.RecordingUnit;
import fr.siamois.domain.models.recordingunit.StratigraphicRelationship;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.infrastructure.database.repositories.recordingunit.StratigraphicRelationshipRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class RecordingUnitStratiRelSeeder {

    private final StratigraphicRelationshipRepository stratigraphicRelationshipRepository;
    private final RecordingUnitSeeder recordingUnitSeeder;
    private final ConceptSeeder conceptSeeder;
    public record RecordingUnitStratiRelDTO(
            String us1,
            String us2,
            ConceptSeeder.ConceptKey rel,
            Boolean conceptDirection,
            Boolean isAsynchronous,
            Boolean isUncertain) {
    }

    public void seed(List<RecordingUnitStratiRelDTO> specs, Long institutionId, String actionUnitIdentifier) {

        for (int i = 0; i < specs.size(); i++) {
            var s = specs.get(i);
            try {
                RecordingUnit us1 = recordingUnitSeeder.getRecordingUnitFromKey(
                        new RecordingUnitSeeder.RecordingUnitKey(s.us1, actionUnitIdentifier),
                        institutionId
                );
                RecordingUnit us2 = recordingUnitSeeder.getRecordingUnitFromKey(
                        new RecordingUnitSeeder.RecordingUnitKey(s.us2, actionUnitIdentifier),
                        institutionId
                );
                Optional<StratigraphicRelationship> toFind = stratigraphicRelationshipRepository.findByUnit1AndUnit2(
                        us1, us2
                );

                Concept rel = s.rel != null
                        ? conceptSeeder.findConceptOrThrow(s.rel)
                        : null;
                if(toFind.isEmpty() && us1 != null && us2 != null && rel != null) {
                    StratigraphicRelationship newRelationship = new StratigraphicRelationship();
                    newRelationship.setUnit1(us1);
                    newRelationship.setUnit2(us2);
                    newRelationship.setConcept(rel);
                    newRelationship.setConceptDirection(s.conceptDirection);
                    newRelationship.setIsAsynchronous(s.isAsynchronous);
                    newRelationship.setUncertain(s.isUncertain);
                    stratigraphicRelationshipRepository.save(newRelationship);
                }
            } catch (Exception e) {
                throw new IllegalStateException(
                        "[Relation strati ligne " + (i + 1) + "] '" + s.us1 + " -> " + s.us2 + "' : " + e.getMessage(), e);
            }
        }

    }
}
