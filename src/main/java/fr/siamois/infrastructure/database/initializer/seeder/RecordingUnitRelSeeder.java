package fr.siamois.infrastructure.database.initializer.seeder;

import fr.siamois.domain.models.recordingunit.RecordingUnit;
import fr.siamois.infrastructure.database.repositories.recordingunit.RecordingUnitRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor
public class RecordingUnitRelSeeder {

    private final RecordingUnitSeeder recordingUnitSeeder;
    private final RecordingUnitRepository recordingUnitRepository;

    public record RecordingUnitRelDTO(String parent, String child) {

    }

    // -------------------------------------------------------------------------
    // Bulk seeding: one query for every recording unit involved (parents and
    // children together) instead of one per row, then a single saveAll — not
    // chunked, since a parent's children set can reference other parents in this
    // same batch, and a chunked flush+clear would detach those cross-referenced
    // entities before a later chunk's save needs them (see SpatialUnitSeeder).
    // -------------------------------------------------------------------------

    public void seed(List<RecordingUnitRelDTO> specs, Long institutionId, String actionUnitIdentifier) {
        if (specs.isEmpty()) return;

        Set<RecordingUnitSeeder.RecordingUnitKey> allKeys = new HashSet<>();
        for (RecordingUnitRelDTO s : specs) {
            allKeys.add(new RecordingUnitSeeder.RecordingUnitKey(s.parent, actionUnitIdentifier));
            allKeys.add(new RecordingUnitSeeder.RecordingUnitKey(s.child, actionUnitIdentifier));
        }
        Map<RecordingUnitSeeder.RecordingUnitKey, RecordingUnit> recordingUnitsByKey =
                recordingUnitSeeder.bulkGetRecordingUnitsFromKeys(allKeys, institutionId);

        // Step 1: Group children by parent identifier
        Map<String, List<RecordingUnit>> parentToChildren = new HashMap<>();
        for (int i = 0; i < specs.size(); i++) {
            var s = specs.get(i);
            try {
                RecordingUnit child = recordingUnitsByKey.get(new RecordingUnitSeeder.RecordingUnitKey(s.child, actionUnitIdentifier));
                if (child == null) throw new IllegalStateException("Recording unit introuvable");
                parentToChildren.computeIfAbsent(s.parent, k -> new ArrayList<>()).add(child);
            } catch (Exception e) {
                throw new IllegalStateException(
                        "[Relation UE ligne " + (i + 1) + "] '" + s.parent + " -> " + s.child + "' : " + e.getMessage(), e);
            }
        }

        // Step 2: Update parents with their children
        List<RecordingUnit> parents = new ArrayList<>();
        for (Map.Entry<String, List<RecordingUnit>> entry : parentToChildren.entrySet()) {
            RecordingUnit parent = recordingUnitsByKey.get(new RecordingUnitSeeder.RecordingUnitKey(entry.getKey(), actionUnitIdentifier));
            if (parent == null) throw new IllegalStateException("Recording unit introuvable");
            parent.getChildren().addAll(entry.getValue());
            parents.add(parent);
        }

        // Step 3: Save all parents at once
        recordingUnitRepository.saveAll(parents);
    }
}
