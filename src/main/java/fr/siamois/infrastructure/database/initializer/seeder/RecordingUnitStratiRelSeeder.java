package fr.siamois.infrastructure.database.initializer.seeder;

import fr.siamois.domain.models.recordingunit.RecordingUnit;
import fr.siamois.domain.models.recordingunit.StratigraphicRelationship;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.infrastructure.database.repositories.recordingunit.StratigraphicRelationshipRepository;
import fr.siamois.infrastructure.database.repositories.vocabulary.ConceptRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RecordingUnitStratiRelSeeder {

    private static final int FLUSH_CHUNK_SIZE = 100;

    private final StratigraphicRelationshipRepository stratigraphicRelationshipRepository;
    private final RecordingUnitSeeder recordingUnitSeeder;
    private final ConceptRepository conceptRepository;

    @PersistenceContext
    private EntityManager entityManager;

    public record RecordingUnitStratiRelDTO(
            String us1,
            String us2,
            ConceptSeeder.ConceptKey rel,
            Boolean conceptDirection,
            Boolean isAsynchronous,
            Boolean isUncertain) {
    }

    // -------------------------------------------------------------------------
    // Bulk seeding: collect distinct lookup keys, fetch each reference type in a
    // handful of queries instead of per-row, then build + batch-write with periodic
    // flush+clear to bound the persistence context for large imports. Safe to chunk
    // since relationship rows never reference each other, only externally
    // pre-existing recording units.
    // -------------------------------------------------------------------------

    public void seed(List<RecordingUnitStratiRelDTO> specs, Long institutionId, String actionUnitIdentifier) {
        if (specs.isEmpty()) return;

        Set<RecordingUnitSeeder.RecordingUnitKey> keys = new HashSet<>();
        for (RecordingUnitStratiRelDTO s : specs) {
            keys.add(new RecordingUnitSeeder.RecordingUnitKey(s.us1, actionUnitIdentifier));
            keys.add(new RecordingUnitSeeder.RecordingUnitKey(s.us2, actionUnitIdentifier));
        }
        Map<RecordingUnitSeeder.RecordingUnitKey, RecordingUnit> recordingUnitsByKey =
                recordingUnitSeeder.bulkGetRecordingUnitsFromKeys(keys, institutionId);
        Map<ConceptSeeder.ConceptKey, Concept> conceptsByKey = fetchConcepts(specs);

        List<StratigraphicRelationship> built = new ArrayList<>();
        Set<String> queuedPairs = new HashSet<>();
        for (int i = 0; i < specs.size(); i++) {
            var s = specs.get(i);
            try {
                RecordingUnit us1 = recordingUnitsByKey.get(new RecordingUnitSeeder.RecordingUnitKey(s.us1, actionUnitIdentifier));
                if (us1 == null) throw new IllegalStateException("Recording unit introuvable");
                RecordingUnit us2 = recordingUnitsByKey.get(new RecordingUnitSeeder.RecordingUnitKey(s.us2, actionUnitIdentifier));
                if (us2 == null) throw new IllegalStateException("Recording unit introuvable");

                Concept rel = null;
                if (s.rel != null) {
                    rel = conceptsByKey.get(s.rel);
                    if (rel == null) throw new IllegalStateException("Concept " + s.rel + " introuvable");
                }

                if (rel != null && queuedPairs.add(us1.getId() + "|" + us2.getId())) {
                    StratigraphicRelationship newRelationship = new StratigraphicRelationship();
                    newRelationship.setUnit1(us1);
                    newRelationship.setUnit2(us2);
                    newRelationship.setConcept(rel);
                    newRelationship.setConceptDirection(s.conceptDirection);
                    newRelationship.setIsAsynchronous(s.isAsynchronous);
                    newRelationship.setUncertain(s.isUncertain);
                    built.add(newRelationship);
                }
            } catch (Exception e) {
                throw new IllegalStateException(
                        "[Relation strati ligne " + (i + 1) + "] '" + s.us1 + " -> " + s.us2 + "' : " + e.getMessage(), e);
            }
        }

        Set<String> existingPairs = fetchExistingPairs(built);
        List<StratigraphicRelationship> toInsert = built.stream()
                .filter(r -> !existingPairs.contains(r.getUnit1().getId() + "|" + r.getUnit2().getId()))
                .toList();

        for (int i = 0; i < toInsert.size(); i += FLUSH_CHUNK_SIZE) {
            List<StratigraphicRelationship> chunk = toInsert.subList(i, Math.min(i + FLUSH_CHUNK_SIZE, toInsert.size()));
            stratigraphicRelationshipRepository.saveAll(chunk);
            entityManager.flush();
            entityManager.clear();
        }
    }

    private Set<String> fetchExistingPairs(List<StratigraphicRelationship> built) {
        Set<Long> unit1Ids = built.stream().map(r -> r.getUnit1().getId()).collect(Collectors.toSet());
        if (unit1Ids.isEmpty()) return Set.of();
        Set<String> result = new HashSet<>();
        for (StratigraphicRelationship r : stratigraphicRelationshipRepository.findAllByUnit1IdIn(unit1Ids)) {
            result.add(r.getUnit1().getId() + "|" + r.getUnit2().getId());
        }
        return result;
    }

    private Map<ConceptSeeder.ConceptKey, Concept> fetchConcepts(List<RecordingUnitStratiRelDTO> specs) {
        Map<String, Set<String>> lowerIdcsByVocab = new HashMap<>();
        for (RecordingUnitStratiRelDTO s : specs) {
            if (s.rel() == null) continue;
            lowerIdcsByVocab.computeIfAbsent(s.rel().vocabularyExtId(), k -> new HashSet<>()).add(s.rel().conceptExtId().toLowerCase());
        }
        Map<ConceptSeeder.ConceptKey, Concept> result = new HashMap<>();
        for (var entry : lowerIdcsByVocab.entrySet()) {
            for (Concept c : conceptRepository.findAllByExternalVocabularyIdIgnoreCaseAndExternalIdIgnoreCaseIn(entry.getKey(), entry.getValue())) {
                result.put(new ConceptSeeder.ConceptKey(entry.getKey(), c.getExternalId()), c);
            }
        }
        return result;
    }
}
