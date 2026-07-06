package fr.siamois.infrastructure.database.initializer.seeder;

import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.misc.ImportProgress;
import fr.siamois.domain.models.phase.Phase;
import fr.siamois.domain.models.vocabulary.Concept;
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
            ActionUnitSeeder.ActionUnitKey actionUnitKey
    ) {}

    // -------------------------------------------------------------------------
    // Bulk seeding: collect distinct lookup keys, fetch each reference type in a
    // handful of queries instead of per-row, then build + batch-write with periodic
    // flush+clear to bound the persistence context for large imports. Safe to chunk
    // since phases never reference each other, only externally pre-existing entities.
    // -------------------------------------------------------------------------

    public void seed(List<PhaseSpecs> specs) {
        seed(specs, new ImportProgress());
    }

    public void seed(List<PhaseSpecs> specs, ImportProgress progress) {
        if (specs.isEmpty()) return;

        Map<ActionUnitSeeder.ActionUnitKey, ActionUnit> actionUnitsByKey = fetchActionUnits(specs);
        Map<ConceptSeeder.ConceptKey, Concept> conceptsByKey = fetchConcepts(specs);
        Map<String, Person> personCache = prefetchPersons(specs);

        List<Phase> toInsert = new ArrayList<>();
        Set<String> queuedKeys = new HashSet<>();
        Map<Long, List<String>> existingIdsByActionUnitId = new HashMap<>();

        for (int i = 0; i < specs.size(); i++) {
            buildPhase(specs.get(i), i, actionUnitsByKey, conceptsByKey, personCache,
                    queuedKeys, existingIdsByActionUnitId).ifPresent(toInsert::add);
        }

        Set<String> existingKeys = fetchExistingPhaseKeys(existingIdsByActionUnitId);
        toInsert.removeIf(phase -> existingKeys.contains(phase.getActionUnit().getId() + "|" + phase.getIdentifier()));

        flushInBatches(toInsert, progress);
        // specs skipped as already-existing or as in-batch duplicates never went into toInsert,
        // so they'd otherwise never be accounted for in the running total.
        progress.advance(specs.size() - toInsert.size());
    }

    private Optional<Phase> buildPhase(PhaseSpecs s, int index,
                                        Map<ActionUnitSeeder.ActionUnitKey, ActionUnit> actionUnitsByKey,
                                        Map<ConceptSeeder.ConceptKey, Concept> conceptsByKey,
                                        Map<String, Person> personCache,
                                        Set<String> queuedKeys,
                                        Map<Long, List<String>> existingIdsByActionUnitId) {
        try {
            ActionUnit au = resolveActionUnit(s, actionUnitsByKey);
            Concept type = resolveType(s, conceptsByKey);
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
            existingIdsByActionUnitId.computeIfAbsent(au.getId(), k -> new ArrayList<>()).add(s.identifier());
            return Optional.of(phase);
        } catch (Exception e) {
            throw new IllegalStateException(
                    "[Phase ligne " + (index + 1) + "] '" + s.identifier() + "' : " + e.getMessage(), e);
        }
    }

    private ActionUnit resolveActionUnit(PhaseSpecs s, Map<ActionUnitSeeder.ActionUnitKey, ActionUnit> actionUnitsByKey) {
        return SeederUtils.field("projet", () -> {
            ActionUnit found = actionUnitsByKey.get(s.actionUnitKey());
            if (found == null) throw new IllegalStateException("Projet introuvable");
            return found;
        });
    }

    private Concept resolveType(PhaseSpecs s, Map<ConceptSeeder.ConceptKey, Concept> conceptsByKey) {
        if (s.type() == null) return null;
        return SeederUtils.field("type", () -> {
            Concept c = conceptsByKey.get(s.type());
            if (c == null) throw new IllegalStateException("Concept " + s.type() + " introuvable");
            return c;
        });
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

    private Set<String> fetchExistingPhaseKeys(Map<Long, List<String>> idsByActionUnitId) {
        Set<String> result = new HashSet<>();
        for (var entry : idsByActionUnitId.entrySet()) {
            for (Phase p : phaseRepository.findAllByIdentifierInAndActionUnitId(entry.getValue(), entry.getKey())) {
                result.add(entry.getKey() + "|" + p.getIdentifier());
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
            if (s.type() == null) continue;
            lowerIdcsByVocab.computeIfAbsent(s.type().vocabularyExtId(), k -> new HashSet<>()).add(s.type().conceptExtId().toLowerCase());
        }
        Map<ConceptSeeder.ConceptKey, Concept> result = new HashMap<>();
        for (var entry : lowerIdcsByVocab.entrySet()) {
            for (Concept c : conceptRepository.findAllByExternalVocabularyIdIgnoreCaseAndExternalIdIgnoreCaseIn(entry.getKey(), entry.getValue())) {
                result.put(new ConceptSeeder.ConceptKey(entry.getKey(), c.getExternalId()), c);
            }
        }
        return result;
    }

    private Map<String, Person> prefetchPersons(List<PhaseSpecs> specs) {
        List<String> nameLastNameStrings = new ArrayList<>();
        for (PhaseSpecs s : specs) {
            if (s.authorEmail() != null && !s.authorEmail().isBlank()) nameLastNameStrings.add(s.authorEmail());
        }
        return personSeeder.prefetchByNameLastName(nameLastNameStrings);
    }
}
