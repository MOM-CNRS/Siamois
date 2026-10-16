package fr.siamois.infrastructure.database.initializer.seeder;

import fr.siamois.domain.models.misc.ImportProgress;
import fr.siamois.domain.models.recordingunit.RecordingUnit;
import fr.siamois.domain.models.relation.RelationDefinition;
import fr.siamois.domain.models.relation.RelationLink;
import fr.siamois.domain.models.relation.SystemRelations;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.services.relation.RelationLinkAttributesService;
import fr.siamois.infrastructure.database.repositories.relation.RelationDefinitionRepository;
import fr.siamois.infrastructure.database.repositories.relation.RelationLinkRepository;
import fr.siamois.infrastructure.database.repositories.vocabulary.ConceptRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Imports the stratigraphic relations of a spreadsheet as links of the platform's two relations
 * ({@link SystemRelations#ORDER_CODE} and {@link SystemRelations#SYNCHRONOUS_CODE}).
 * <p>
 * A row {@code us1, us2} keeps the sheet's convention: for an asynchronous relation {@code us1} is the anterior unit and
 * {@code us2} the posterior one, so the link has {@code us2} as source and {@code us1} as target. The sheet's concept is
 * the link's type on the end it describes: {@code conceptDirection} says it reads from {@code us1} (the target end) or from
 * {@code us2} (the source end). A synchronous row becomes a link of the symmetric relation, its lower id as source.
 * "Uncertain" is the link's own attribute. An import is a trusted bulk write: it does not apply the relations' rules.
 */
@Service
@RequiredArgsConstructor
public class RecordingUnitStratiRelSeeder {

    private static final int FLUSH_CHUNK_SIZE = 100;
    private static final int LOOKUP_CHUNK_SIZE = 1000;

    private final RelationDefinitionRepository relationDefinitionRepository;
    private final RelationLinkRepository relationLinkRepository;
    private final RelationLinkAttributesService relationLinkAttributesService;
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

    /** A link to write: its two ends and the sheet's own flags, before it is a {@link RelationLink}. */
    private record Planned(RelationDefinition definition, RecordingUnit source, RecordingUnit target,
                           Concept sourceType, Concept targetType, boolean uncertain) {

        String pairKey() {
            return definition.getCode() + "|" + source.getId() + "|" + target.getId();
        }
    }

    // -------------------------------------------------------------------------
    // Bulk seeding: collect distinct lookup keys, fetch each reference type in a
    // handful of queries instead of per-row, then build + batch-write with periodic
    // flush+clear to bound the persistence context for large imports. Safe to chunk
    // since links never reference each other, only externally pre-existing recording
    // units.
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
        RelationDefinition order = definition(SystemRelations.ORDER_CODE);
        RelationDefinition synchronous = definition(SystemRelations.SYNCHRONOUS_CODE);

        List<Planned> planned = new ArrayList<>();
        Set<String> queuedPairs = new HashSet<>();
        for (int i = 0; i < specs.size(); i++) {
            plan(specs.get(i), i, actionUnitIdentifier, recordingUnitsByKey, conceptsByKey, institutionId, order, synchronous)
                    .filter(p -> queuedPairs.add(p.pairKey()))
                    .ifPresent(planned::add);
        }

        Set<String> existingPairs = fetchExistingPairs(planned);
        List<Planned> toInsert = planned.stream().filter(p -> !existingPairs.contains(p.pairKey())).toList();

        flushInBatches(toInsert, progress);
        // specs skipped (in-batch duplicate, or already-existing) never went into toInsert,
        // so they'd otherwise never be accounted for in the running total.
        progress.advance(specs.size() - toInsert.size());
    }

    private RelationDefinition definition(String code) {
        return relationDefinitionRepository.findByCode(code)
                .orElseThrow(() -> new IllegalStateException("Relation " + code + " introuvable : les relations de la plateforme ne sont pas initialisées"));
    }

    private Optional<Planned> plan(RecordingUnitStratiRelDTO s, int index, String actionUnitIdentifier,
                                   Map<RecordingUnitSeeder.RecordingUnitKey, RecordingUnit> recordingUnitsByKey,
                                   Map<ConceptSeeder.ConceptKey, Concept> conceptsByKey, Long institutionId,
                                   RelationDefinition order, RelationDefinition synchronous) {
        try {
            RecordingUnit us1 = resolveRecordingUnit(s.us1, actionUnitIdentifier, recordingUnitsByKey);
            RecordingUnit us2 = resolveRecordingUnit(s.us2, actionUnitIdentifier, recordingUnitsByKey);
            Concept rel = resolveRelConcept(s, conceptsByKey, institutionId);
            boolean uncertain = Boolean.TRUE.equals(s.isUncertain);

            if (Boolean.FALSE.equals(s.isAsynchronous)) {
                RecordingUnit lower = us1.getId() <= us2.getId() ? us1 : us2;
                RecordingUnit higher = lower == us1 ? us2 : us1;
                return Optional.of(new Planned(synchronous, lower, higher, rel, null, uncertain));
            }
            boolean readsFromUs1 = Boolean.TRUE.equals(s.conceptDirection);
            return Optional.of(new Planned(order, us2, us1, readsFromUs1 ? null : rel, readsFromUs1 ? rel : null, uncertain));
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
        if (rel == null) throw new IllegalStateException(conceptSeeder.describeMissingConcept(s.rel, institutionId));
        return rel;
    }

    private void flushInBatches(List<Planned> toInsert, ImportProgress progress) {
        for (int i = 0; i < toInsert.size(); i += FLUSH_CHUNK_SIZE) {
            List<Planned> chunk = toInsert.subList(i, Math.min(i + FLUSH_CHUNK_SIZE, toInsert.size()));
            List<RelationLink> links = new ArrayList<>();
            for (Planned p : chunk) {
                RelationLink link = new RelationLink();
                link.setDefinition(p.definition());
                link.attachSource(p.source());
                link.attachTarget(p.target());
                link.setSourceType(p.sourceType());
                link.setTargetType(p.targetType());
                links.add(link);
            }
            relationLinkRepository.saveAll(links);
            entityManager.flush();
            for (int k = 0; k < chunk.size(); k++) {
                if (chunk.get(k).uncertain()) {
                    // on behalf of whoever created the unit: an import has no user of its own
                    relationLinkAttributesService.save(links.get(k), Map.of(SystemRelations.UNCERTAIN_FIELD_ID, true),
                            chunk.get(k).source().getCreatedBy());
                }
            }
            entityManager.flush();
            entityManager.clear();
            progress.advance(chunk.size());
            SeederUtils.logBatch("RecordingUnitStratiRelSeeder", i + chunk.size(), FLUSH_CHUNK_SIZE, toInsert.size());
        }
    }

    /** The pairs among the planned ones that are already stored, in the same key form as {@link Planned#pairKey()}. */
    private Set<String> fetchExistingPairs(List<Planned> planned) {
        List<Long> sourceIds = planned.stream().map(p -> p.source().getId()).distinct().toList();
        Set<String> result = new HashSet<>();
        for (int i = 0; i < sourceIds.size(); i += LOOKUP_CHUNK_SIZE) {
            List<Long> chunk = sourceIds.subList(i, Math.min(i + LOOKUP_CHUNK_SIZE, sourceIds.size()));
            List<?> rows = entityManager.createQuery("select l.definition.code, l.sourceRecordingUnit.id, l.targetRecordingUnit.id "
                            + "from RelationLink l where l.sourceRecordingUnit.id in :ids and l.targetRecordingUnit is not null")
                    .setParameter("ids", chunk)
                    .getResultList();
            for (Object row : rows) {
                Object[] cols = (Object[]) row;
                result.add(cols[0] + "|" + cols[1] + "|" + cols[2]);
            }
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
