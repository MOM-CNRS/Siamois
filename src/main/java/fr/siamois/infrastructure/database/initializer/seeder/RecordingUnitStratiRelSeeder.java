package fr.siamois.infrastructure.database.initializer.seeder;

import fr.siamois.domain.models.misc.ImportProgress;
import fr.siamois.domain.models.recordingunit.RecordingUnit;
import fr.siamois.domain.models.relation.RelationDefinition;
import fr.siamois.domain.models.relation.RelationEndKind;
import fr.siamois.domain.models.relation.RelationLink;
import fr.siamois.domain.services.relation.RelationRuleResolver;
import fr.siamois.domain.services.relation.RelationRuleSet;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.infrastructure.database.repositories.relation.RelationDefinitionRepository;
import fr.siamois.infrastructure.database.repositories.relation.RelationField;
import fr.siamois.infrastructure.database.repositories.relation.RelationLinkRepository;
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

    private final RelationLinkRepository linkRepository;
    private final RelationDefinitionRepository definitionRepository;
    private final RelationRuleResolver ruleResolver;
    private final RecordingUnitSeeder recordingUnitSeeder;
    private final ConceptRepository conceptRepository;
    private final ConceptSeeder conceptSeeder;

    @PersistenceContext
    private EntityManager entityManager;

    public record RecordingUnitStratiRelDTO(
            String us1,
            String us2,
            ConceptSeeder.ConceptKey rel,
            Boolean conceptDirection,
            Boolean isAsynchronous,
            Boolean isUncertain,
            Integer excelRowNumber) {
    }

    // -------------------------------------------------------------------------
    // Bulk seeding: collect distinct lookup keys, fetch each reference type in a
    // handful of queries instead of per-row, then build + batch-write with periodic
    // flush+clear to bound the persistence context for large imports. Safe to chunk
    // since relationship rows never reference each other, only externally
    // pre-existing recording units.
    // -------------------------------------------------------------------------

    public void seed(List<RecordingUnitStratiRelDTO> specs, Long institutionId, String actionUnitIdentifier) {
        seed(specs, institutionId, actionUnitIdentifier, new ImportProgress());
    }

    public void seed(List<RecordingUnitStratiRelDTO> specs, Long institutionId, String actionUnitIdentifier, ImportProgress progress) {
        if (specs.isEmpty()) return;

        Set<RecordingUnitSeeder.RecordingUnitKey> keys = new HashSet<>();
        for (RecordingUnitStratiRelDTO s : specs) {
            keys.add(new RecordingUnitSeeder.RecordingUnitKey(s.us1, actionUnitIdentifier));
            keys.add(new RecordingUnitSeeder.RecordingUnitKey(s.us2, actionUnitIdentifier));
        }
        Map<RecordingUnitSeeder.RecordingUnitKey, RecordingUnit> recordingUnitsByKey =
                recordingUnitSeeder.bulkGetRecordingUnitsFromKeys(keys, institutionId);
        Map<ConceptSeeder.ConceptKey, Concept> conceptsByKey = fetchConcepts(specs);

        Definitions definitions = new Definitions(
                definitionRepository.findByField(RelationField.Stratigraphy.ORDER_SOURCE_FIELD).orElseThrow(),
                definitionRepository.findByField(RelationField.Stratigraphy.SYNCHRONY_FIELD).orElseThrow(), new HashMap<>());
        List<RelationLink> built = new ArrayList<>();
        Set<String> queuedPairs = new HashSet<>();
        for (int i = 0; i < specs.size(); i++) {
            buildRelationship(specs.get(i), i, actionUnitIdentifier, recordingUnitsByKey, conceptsByKey, queuedPairs,
                    institutionId, definitions).ifPresent(built::add);
        }

        Set<String> existingPairs = fetchExistingPairs(built);
        List<RelationLink> toInsert = built.stream()
                .filter(l -> !existingPairs.contains(pairOf(l)))
                .toList();

        flushInBatches(toInsert, progress);
        // specs skipped (null rel, in-batch duplicate, or already-existing) never went into toInsert,
        // so they'd otherwise never be accounted for in the running total.
        progress.advance(specs.size() - toInsert.size());
    }

    /** The two stratigraphic definitions, and the order's rules of each project met (for its mirror pairs). */
    private record Definitions(RelationDefinition order, RelationDefinition synchrony, Map<Long, RelationRuleSet> orderRules) {
    }

    /**
     * One relation of the file, as a link (docs/reprise-stratigraphie.md, §3): us1 is the anterior unit and us2 the
     * posterior one, the order goes from us2 to us1 with the qualifier as us2 reads it (the concept it mirrors when
     * the file read it from us1), the synchrony stores its pair in one order.
     */
    private Optional<RelationLink> buildRelationship(RecordingUnitStratiRelDTO s, int index, String actionUnitIdentifier,
                                                     Map<RecordingUnitSeeder.RecordingUnitKey, RecordingUnit> recordingUnitsByKey,
                                                     Map<ConceptSeeder.ConceptKey, Concept> conceptsByKey,
                                                     Set<String> queuedPairs, Long institutionId, Definitions definitions) {
        try {
            RecordingUnit us1 = resolveRecordingUnit(s.us1, actionUnitIdentifier, recordingUnitsByKey);
            RecordingUnit us2 = resolveRecordingUnit(s.us2, actionUnitIdentifier, recordingUnitsByKey);
            Concept rel = resolveRelConcept(s, conceptsByKey, institutionId);
            if (us1.getActionUnit() == null || us2.getActionUnit() == null
                    || !us1.getActionUnit().getId().equals(us2.getActionUnit().getId())) {
                throw new IllegalStateException("les deux unités doivent appartenir au même projet");
            }
            long project = us1.getActionUnit().getId();
            boolean asynchronous = !Boolean.FALSE.equals(s.isAsynchronous);

            RelationLink link = new RelationLink();
            link.setProjectId(project);
            link.setSourceKind(RelationEndKind.RECORDING_UNIT);
            link.setTargetKind(RelationEndKind.RECORDING_UNIT);
            link.setUncertain(Boolean.TRUE.equals(s.isUncertain));
            link.setSimple(false);
            if (asynchronous) {
                link.setDefinition(definitions.order());
                link.setSourceId(us2.getId());
                link.setTargetId(us1.getId());
                if (rel != null) {
                    RelationRuleSet rules = definitions.orderRules().computeIfAbsent(project,
                            p -> ruleResolver.rulesFor(definitions.order(), p));
                    Long stored = rules.qualifierForMirror(rel.getId());
                    link.setQualifier(stored.equals(rel.getId()) ? rel : conceptRepository.findById(stored).orElse(rel));
                }
            } else {
                link.setDefinition(definitions.synchrony());
                link.setSourceId(Math.min(us1.getId(), us2.getId()));
                link.setTargetId(Math.max(us1.getId(), us2.getId()));
                link.setQualifier(rel);
            }
            if (!queuedPairs.add(pairOf(link))) return Optional.empty();
            return Optional.of(link);
        } catch (Exception e) {
            throw new IllegalStateException(
                    "[Relation strati ligne " + SeederUtils.lineNumber(s.excelRowNumber(), index) + "] '" + s.us1 + " -> " + s.us2 + "' : " + e.getMessage(), e);
        }
    }

    private RecordingUnit resolveRecordingUnit(String identifier, String actionUnitIdentifier,
                                                Map<RecordingUnitSeeder.RecordingUnitKey, RecordingUnit> recordingUnitsByKey) {
        RecordingUnit ru = recordingUnitsByKey.get(new RecordingUnitSeeder.RecordingUnitKey(identifier, actionUnitIdentifier));
        if (ru == null) throw new IllegalStateException("Recording unit introuvable");
        return ru;
    }

    private Concept resolveRelConcept(RecordingUnitStratiRelDTO s, Map<ConceptSeeder.ConceptKey, Concept> conceptsByKey, Long institutionId) {
        if (s.rel == null) return null;
        Concept rel = conceptsByKey.get(s.rel);
        if (rel == null) throw MissingConceptException.of(conceptSeeder, s.rel, institutionId);
        return rel;
    }

    private void flushInBatches(List<RelationLink> toInsert, ImportProgress progress) {
        for (int i = 0; i < toInsert.size(); i += FLUSH_CHUNK_SIZE) {
            List<RelationLink> chunk = toInsert.subList(i, Math.min(i + FLUSH_CHUNK_SIZE, toInsert.size()));
            linkRepository.saveAll(chunk);
            entityManager.flush();
            entityManager.clear();
            progress.advance(chunk.size());
            SeederUtils.logBatch("RecordingUnitStratiRelSeeder", i + chunk.size(), FLUSH_CHUNK_SIZE, toInsert.size());
        }
    }

    /** The pairs the stratigraphy already links, whichever its definition: a file line already there is skipped. */
    private Set<String> fetchExistingPairs(List<RelationLink> built) {
        Set<Long> sources = built.stream().map(RelationLink::getSourceId).collect(Collectors.toSet());
        if (sources.isEmpty()) return Set.of();
        Set<String> result = new HashSet<>();
        for (RelationLink existing : linkRepository.findStratigraphicFromSources(sources)) {
            result.add(pairOf(existing));
        }
        return result;
    }

    private static String pairOf(RelationLink link) {
        return link.getDefinition().getId() + "|" + link.getSourceId() + "|" + link.getTargetId();
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
