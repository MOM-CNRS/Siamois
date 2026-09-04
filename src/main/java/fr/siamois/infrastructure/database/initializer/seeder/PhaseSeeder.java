package fr.siamois.infrastructure.database.initializer.seeder;

import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.misc.ImportProgress;
import fr.siamois.domain.models.misc.SeedCounts;
import fr.siamois.domain.models.phase.Phase;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.infrastructure.dataimport.ImportSchema;
import fr.siamois.infrastructure.database.repositories.PhaseRepository;
import fr.siamois.infrastructure.database.repositories.actionunit.ActionUnitRepository;
import fr.siamois.infrastructure.database.repositories.vocabulary.ConceptRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PhaseSeeder {

    private static final int FLUSH_CHUNK_SIZE = 100;

    private final PhaseRepository phaseRepository;
    private final ActionUnitRepository actionUnitRepository;
    private final PersonSeeder personSeeder;
    private final ConceptRepository conceptRepository;
    private final ConceptSeeder conceptSeeder;

    @PersistenceContext
    private EntityManager entityManager;

    public record PhaseSpecs(
            String identifier,
            String title,
            ConceptSeeder.ConceptKey type,
            String description,
            Integer orderNumber,
            Integer lowerBound,
            Integer upperBound,
            String authorEmail,
            ActionUnitSeeder.ActionUnitKey actionUnitKey,
            Set<ConceptSeeder.ConceptKey> periods,
            Set<ConceptSeeder.ConceptKey> keywords,
            Integer excelRowNumber
    ) {}

    public void seed(List<PhaseSpecs> specs) {
        seed(specs, new ImportProgress());
    }

    /**
     * Bulk-seeds phases: resolves each spec's action unit, type concept and author in a handful of
     * queries, deduplicates against already-queued and already-existing phases, then persists the
     * rest in chunked batches.
     *
     * @param specs phase specs to seed; a no-op if empty
     * @param progress advanced by the number of specs accounted for (persisted or skipped as duplicates)
     */
    public void seed(List<PhaseSpecs> specs, ImportProgress progress) {
        seed(specs, progress, new SeedCounts());
    }

    public void seed(List<PhaseSpecs> specs, ImportProgress progress, SeedCounts seedCounts) {
        if (specs.isEmpty()) return;

        Map<ActionUnitSeeder.ActionUnitKey, ActionUnit> actionUnitsByKey = fetchActionUnits(specs);
        Map<ConceptSeeder.ConceptKey, Concept> conceptsByKey = fetchConcepts(specs);
        Map<String, Person> personCache = prefetchPersons(specs);

        List<Phase> built = new ArrayList<>();
        Set<String> queuedKeys = new HashSet<>();
        Map<Long, List<String>> existingIdsByActionUnitId = new HashMap<>();

        for (int i = 0; i < specs.size(); i++) {
            buildPhase(specs.get(i), i, actionUnitsByKey, conceptsByKey, personCache,
                    queuedKeys, existingIdsByActionUnitId).ifPresent(built::add);
        }

        Map<String, Phase> existingByKey = fetchExistingPhases(existingIdsByActionUnitId);
        List<Phase> toInsert = new ArrayList<>();
        List<Phase> toUpdate = new ArrayList<>();
        for (Phase phase : built) {
            String key = phase.getActionUnit().getId() + "|" + phase.getIdentifier();
            Phase existing = existingByKey.get(key);
            if (existing != null) {
                mergePhaseInto(phase, existing);
                toUpdate.add(existing);
            } else {
                toInsert.add(phase);
            }
        }

        flushInBatches(toInsert, progress);
        flushInBatches(toUpdate, progress);
        // specs skipped as in-batch duplicates never went into toInsert/toUpdate,
        // so they'd otherwise never be accounted for in the running total.
        progress.advance(specs.size() - toInsert.size() - toUpdate.size());
        seedCounts.record(ImportSchema.PHASE, toInsert.size(), toUpdate.size(),
                specs.size() - toInsert.size() - toUpdate.size());
    }

    /**
     * Overwrites the content fields of an already-persisted phase with those of a freshly-built one
     * from a re-import, so re-importing the same file with corrected values updates the existing row
     * instead of being a no-op. Identity (actionUnit/identifier) and provenance (createdBy) are left
     * untouched.
     */
    private void mergePhaseInto(Phase built, Phase existing) {
        existing.setTitle(built.getTitle());
        existing.setType(built.getType());
        existing.setDescription(built.getDescription());
        existing.setOrderNumber(built.getOrderNumber());
        existing.setLowerBound(built.getLowerBound());
        existing.setUpperBound(built.getUpperBound());
        existing.setAuthor(built.getAuthor());
        existing.setPeriods(built.getPeriods());
        existing.setKeywords(built.getKeywords());
    }

    private Optional<Phase> buildPhase(PhaseSpecs s, int index,
                                        Map<ActionUnitSeeder.ActionUnitKey, ActionUnit> actionUnitsByKey,
                                        Map<ConceptSeeder.ConceptKey, Concept> conceptsByKey,
                                        Map<String, Person> personCache,
                                        Set<String> queuedKeys,
                                        Map<Long, List<String>> existingIdsByActionUnitId) {
        try {
            ActionUnit au = resolveActionUnit(s, actionUnitsByKey);
            Long institutionId = au.getCreatedByInstitution().getId();
            Concept type = resolveType(s, conceptsByKey, institutionId);
            Person author = resolveAuthor(s, personCache);

            String dedupKey = au.getId() + "|" + s.identifier();
            if (!queuedKeys.add(dedupKey)) return Optional.empty();

            Phase phase = new Phase();
            phase.setIdentifier(s.identifier());
            phase.setTitle(s.title());
            phase.setActionUnit(au);
            phase.setType(type);
            phase.setDescription(s.description());
            phase.setOrderNumber(s.orderNumber());
            phase.setLowerBound(s.lowerBound());
            phase.setUpperBound(s.upperBound());
            phase.setCreatedByInstitution(au.getCreatedByInstitution());
            phase.setAuthor(author);
            phase.setCreatedBy(author);
            phase.setPeriods(resolveConceptSet(s.periods(), conceptsByKey, institutionId));
            phase.setKeywords(resolveConceptSet(s.keywords(), conceptsByKey, institutionId));
            existingIdsByActionUnitId.computeIfAbsent(au.getId(), k -> new ArrayList<>()).add(s.identifier());
            return Optional.of(phase);
        } catch (Exception e) {
            throw new IllegalStateException(
                    "[Phase ligne " + SeederUtils.lineNumber(s.excelRowNumber(), index) + "] '" + s.identifier() + "' : " + e.getMessage(), e);
        }
    }

    private ActionUnit resolveActionUnit(PhaseSpecs s, Map<ActionUnitSeeder.ActionUnitKey, ActionUnit> actionUnitsByKey) {
        return SeederUtils.field("projet", () -> {
            ActionUnit found = actionUnitsByKey.get(s.actionUnitKey());
            if (found == null) throw new IllegalStateException("Projet introuvable");
            return found;
        });
    }

    private Concept resolveType(PhaseSpecs s, Map<ConceptSeeder.ConceptKey, Concept> conceptsByKey, Long institutionId) {
        if (s.type() == null) return null;
        return SeederUtils.field("type", () -> {
            Concept c = conceptsByKey.get(s.type());
            if (c == null) throw new IllegalStateException(conceptSeeder.describeMissingConcept(s.type(), institutionId));
            return c;
        });
    }

    private Set<Concept> resolveConceptSet(Set<ConceptSeeder.ConceptKey> keys, Map<ConceptSeeder.ConceptKey, Concept> conceptsByKey, Long institutionId) {
        if (keys == null || keys.isEmpty()) return new HashSet<>();
        Set<Concept> result = new HashSet<>();
        for (ConceptSeeder.ConceptKey key : keys) {
            Concept c = conceptsByKey.get(key);
            if (c == null) throw new IllegalStateException(conceptSeeder.describeMissingConcept(key, institutionId));
            result.add(c);
        }
        return result;
    }

    private Person resolveAuthor(PhaseSpecs s, Map<String, Person> personCache) {
        if (s.authorEmail() == null || s.authorEmail().isBlank()) return null;
        return SeederUtils.field("auteur", () -> personSeeder.resolveCached(personCache, s.authorEmail()));
    }

    private void flushInBatches(List<Phase> toInsert, ImportProgress progress) {
        for (int i = 0; i < toInsert.size(); i += FLUSH_CHUNK_SIZE) {
            List<Phase> chunk = toInsert.subList(i, Math.min(i + FLUSH_CHUNK_SIZE, toInsert.size()));
            phaseRepository.saveAll(chunk);
            entityManager.flush();
            entityManager.clear();
            progress.advance(chunk.size());
            SeederUtils.logBatch("PhaseSeeder", i + chunk.size(), FLUSH_CHUNK_SIZE, toInsert.size());
        }
    }

    private Map<String, Phase> fetchExistingPhases(Map<Long, List<String>> idsByActionUnitId) {
        Map<String, Phase> result = new HashMap<>();
        for (var entry : idsByActionUnitId.entrySet()) {
            for (Phase p : phaseRepository.findAllByIdentifierInAndActionUnitId(entry.getValue(), entry.getKey())) {
                result.put(entry.getKey() + "|" + p.getIdentifier(), p);
            }
        }
        return result;
    }

    private Map<ActionUnitSeeder.ActionUnitKey, ActionUnit> fetchActionUnits(List<PhaseSpecs> specs) {
        Set<ActionUnitSeeder.ActionUnitKey> keys = specs.stream().map(PhaseSpecs::actionUnitKey)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        if (keys.isEmpty()) return Map.of();
        Map<String, List<String>> identifiersByInstitution = keys.stream()
                .collect(Collectors.groupingBy(ActionUnitSeeder.ActionUnitKey::institutionIdentifier,
                        Collectors.mapping(ActionUnitSeeder.ActionUnitKey::fullIdentifier, Collectors.toList())));
        Map<ActionUnitSeeder.ActionUnitKey, ActionUnit> result = new HashMap<>();
        for (var entry : identifiersByInstitution.entrySet()) {
            for (ActionUnit au : actionUnitRepository.findAllByIdentifierInAndCreatedByInstitutionIdentifier(entry.getValue(), entry.getKey())) {
                result.put(new ActionUnitSeeder.ActionUnitKey(au.getFullIdentifier(), entry.getKey()), au);
            }
        }
        return result;
    }

    private Map<ConceptSeeder.ConceptKey, Concept> fetchConcepts(List<PhaseSpecs> specs) {
        Map<String, Set<String>> lowerIdcsByVocab = new HashMap<>();
        for (PhaseSpecs s : specs) {
            addConceptKey(lowerIdcsByVocab, s.type());
            if (s.periods() != null) s.periods().forEach(k -> addConceptKey(lowerIdcsByVocab, k));
            if (s.keywords() != null) s.keywords().forEach(k -> addConceptKey(lowerIdcsByVocab, k));
        }
        Map<ConceptSeeder.ConceptKey, Concept> result = new HashMap<>();
        for (var entry : lowerIdcsByVocab.entrySet()) {
            for (Concept c : conceptRepository.findAllByExternalVocabularyIdIgnoreCaseAndExternalIdIgnoreCaseIn(entry.getKey(), entry.getValue())) {
                result.put(new ConceptSeeder.ConceptKey(entry.getKey(), c.getExternalId()), c);
            }
        }
        return result;
    }

    private void addConceptKey(Map<String, Set<String>> lowerIdcsByVocab, ConceptSeeder.ConceptKey key) {
        if (key == null) return;
        lowerIdcsByVocab.computeIfAbsent(key.vocabularyExtId(), k -> new HashSet<>()).add(key.conceptExtId().toLowerCase());
    }

    private Map<String, Person> prefetchPersons(List<PhaseSpecs> specs) {
        List<String> nameLastNameStrings = new ArrayList<>();
        for (PhaseSpecs s : specs) {
            if (s.authorEmail() != null && !s.authorEmail().isBlank()) nameLastNameStrings.add(s.authorEmail());
        }
        return personSeeder.prefetchByNameLastName(nameLastNameStrings);
    }
}
