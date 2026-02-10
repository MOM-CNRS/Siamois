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

    public void seed(List<RecordingUnitStratiRelDTO> specs) {

        for (var s : specs) {
            RecordingUnit us1;
            RecordingUnit us2;
            Optional<StratigraphicRelationship> toFind;
            try {
                us1 = recordingUnitSeeder.getRecordingUnitFromKey(
                        new RecordingUnitSeeder.RecordingUnitKey(s.us1)
                );
                us2 = recordingUnitSeeder.getRecordingUnitFromKey(
                        new RecordingUnitSeeder.RecordingUnitKey(s.us2)
                );
                toFind = stratigraphicRelationshipRepository.findByUnit1AndUnit2(
                        us1, us2
                );
            }
            catch(Exception e) {
                // no op
                break;
            }

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
        }

    }
}
