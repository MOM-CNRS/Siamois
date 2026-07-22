package fr.siamois.infrastructure.database.initializer.seeder;

import fr.siamois.domain.models.spatialunit.SpatialUnit;
import fr.siamois.infrastructure.database.repositories.SpatialUnitRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor
public class SpatialUnitRelSeeder {

    private final SpatialUnitRepository spatialUnitRepository;

    public record SpatialUnitRelDTO(String parent, String child) {

    }

    /**
     * Bulk-seeds parent/child relations between spatial units by adding each child to its parent's
     * children set and saving all affected parents in one batch. Resolves names against
     * already-persisted spatial units, so this must run after {@link SpatialUnitSeeder#seed}.
     *
     * @param specs parent/child name pairs to link; a no-op if empty
     * @param institutionId institution the spatial units belong to, used to resolve names
     */
    public void seed(List<SpatialUnitRelDTO> specs, Long institutionId) {
        if (specs.isEmpty()) return;

        Set<String> upperNames = new HashSet<>();
        for (SpatialUnitRelDTO s : specs) {
            upperNames.add(s.parent().toUpperCase());
            upperNames.add(s.child().toUpperCase());
        }
        Map<String, SpatialUnit> byUpperName = new HashMap<>();
        for (SpatialUnit su : spatialUnitRepository.findAllByNameInAndInstitution(upperNames, institutionId)) {
            byUpperName.put(su.getName().toUpperCase(), su);
        }

        // Step 1: Group children by parent name
        Map<String, List<SpatialUnit>> parentToChildren = new HashMap<>();
        for (int i = 0; i < specs.size(); i++) {
            var s = specs.get(i);
            try {
                SpatialUnit child = byUpperName.get(s.child().toUpperCase());
                if (child == null) throw new IllegalStateException("Lieu introuvable : " + s.child());
                parentToChildren.computeIfAbsent(s.parent(), k -> new ArrayList<>()).add(child);
            } catch (Exception e) {
                throw new IllegalStateException(
                        "[Relation Lieu ligne " + (i + 1) + "] '" + s.parent() + " -> " + s.child() + "' : " + e.getMessage(), e);
            }
        }

        // Step 2: Update parents with their children
        List<SpatialUnit> parents = new ArrayList<>();
        for (Map.Entry<String, List<SpatialUnit>> entry : parentToChildren.entrySet()) {
            SpatialUnit parent = byUpperName.get(entry.getKey().toUpperCase());
            if (parent == null) throw new IllegalStateException("Lieu introuvable : " + entry.getKey());
            parent.getChildren().addAll(entry.getValue());
            parents.add(parent);
        }

        // Step 3: Save all parents at once
        spatialUnitRepository.saveAll(parents);
        SeederUtils.logBatch("SpatialUnitRelSeeder", parents.size(), Math.max(1, parents.size()), parents.size());
    }
}
