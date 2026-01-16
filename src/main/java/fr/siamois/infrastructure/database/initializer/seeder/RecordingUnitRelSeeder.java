package fr.siamois.infrastructure.database.initializer.seeder;

import fr.siamois.domain.models.recordingunit.RecordingUnit;
import fr.siamois.infrastructure.database.repositories.recordingunit.RecordingUnitRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class RecordingUnitRelSeeder {

    private final RecordingUnitSeeder recordingUnitSeeder;
    private final RecordingUnitRepository recordingUnitRepository;

    public record RecordingUnitDTO(String parent, String child) {

    }

    public void seed(List<RecordingUnitDTO> specs) {
        // Step 1: Group children by parent identifier
        Map<String, List<RecordingUnit>> parentToChildren = new HashMap<>();
        for (var s : specs) {
            String parentKey = s.parent;
            RecordingUnit child = recordingUnitSeeder.getRecordingUnitFromKey(
                    new RecordingUnitSeeder.RecordingUnitKey(s.child)
            );
            parentToChildren.computeIfAbsent(parentKey, k -> new ArrayList<>()).add(child);
        }

        // Step 2: Update parents with their children
        for (Map.Entry<String, List<RecordingUnit>> entry : parentToChildren.entrySet()) {
            RecordingUnit parent = recordingUnitSeeder.getRecordingUnitFromKey(
                    new RecordingUnitSeeder.RecordingUnitKey(entry.getKey())
            );
            parent.getChildren().addAll(entry.getValue());
        }

        // Step 3: Save all parents at once
        recordingUnitRepository.saveAll(parentToChildren.keySet().stream()
                .map(key -> recordingUnitSeeder.getRecordingUnitFromKey(
                        new RecordingUnitSeeder.RecordingUnitKey(key)
                ))
                .toList());
    }
}
