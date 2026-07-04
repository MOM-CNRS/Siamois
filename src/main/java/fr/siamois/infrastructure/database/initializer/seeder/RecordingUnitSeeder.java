package fr.siamois.infrastructure.database.initializer.seeder;


import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.institution.Institution;
import fr.siamois.domain.models.misc.ImportProgress;
import fr.siamois.domain.models.phase.Phase;
import fr.siamois.domain.models.recordingunit.RecordingUnit;
import fr.siamois.domain.models.spatialunit.SpatialUnit;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.infrastructure.database.repositories.PhaseRepository;
import fr.siamois.infrastructure.database.repositories.SpatialUnitRepository;
import fr.siamois.infrastructure.database.repositories.actionunit.ActionUnitRepository;
import fr.siamois.infrastructure.database.repositories.institution.InstitutionRepository;
import fr.siamois.infrastructure.database.repositories.recordingunit.RecordingUnitRepository;
import fr.siamois.infrastructure.database.repositories.vocabulary.ConceptRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;


@Service
@RequiredArgsConstructor
public class RecordingUnitSeeder {

    private static final int FLUSH_CHUNK_SIZE = 100;

    private final RecordingUnitRepository recordingUnitRepository;
    private final SpatialUnitRepository spatialUnitRepository;
    private final ActionUnitRepository actionUnitRepository;
    private final PersonSeeder personSeeder;
    private final PhaseRepository phaseRepository;
    private final InstitutionRepository institutionRepository;
    private final ConceptRepository conceptRepository;

    @PersistenceContext
    private EntityManager entityManager;

    public record RecordingUnitSpecs(String fullIdentifier, Integer identifier,
                                     ConceptSeeder.ConceptKey type,
                                     ConceptSeeder.ConceptKey geomorphologicalCycle,
                                     ConceptSeeder.ConceptKey geomorphologicalAgent,
                                     ConceptSeeder.ConceptKey interpretation,
                                     String authorEmail,
                                     String institutionIdentifier,
                                     String author,
                                     String createdBy,
                                     List<String> excavators,
                                     OffsetDateTime creationTime,
                                     OffsetDateTime beginDate,
                                     OffsetDateTime endDate,
                                     SpatialUnitSeeder.SpatialUnitKey spatialUnitName,
                                     ActionUnitSeeder.ActionUnitKey actionUnitIdentifier,
                                     String description,
                                     String matrixColor,
                                     String matrixComposition,
                                     String matrixTexture,
                                     List<String> phaseIdentifiers) {

    }

    public record RecordingUnitKey(String fullIdentifier, String actionIdentifier) {
    }

    private void getOrCreateRecordingUnit(RecordingUnit recordingUnit) {

        Optional<RecordingUnit> opt = recordingUnitRepository.findByFullIdentifierAndInstitutionIdAndActionUnitFullIdentifier(
                recordingUnit.getFullIdentifier(),
                recordingUnit.getCreatedByInstitution().getId(),
                recordingUnit.getActionUnit().getFullIdentifier());
        if (opt.isEmpty()) {
            recordingUnitRepository.save(recordingUnit);
        }
    }


    public ActionUnit getActionUnitFromKey(ActionUnitSeeder.ActionUnitKey key) {
        return actionUnitRepository.findByIdentifierAndCreatedByInstitutionIdentifier(key.fullIdentifier(), key.institutionIdentifier())
                .orElseThrow(() -> new IllegalStateException("Action introuvable"));
    }

    public SpatialUnit getSpatialUnitFromKey(SpatialUnitSeeder.SpatialUnitKey key, Institution i) {
        return spatialUnitRepository.findByNameAndInstitution(key.unitName(), i.getId())
                .orElseThrow(() -> new IllegalStateException("Lieu "+key.unitName()+" introuvable"));
    }

    public RecordingUnit getRecordingUnitFromKey(RecordingUnitKey key, Long institutionId) {
        return recordingUnitRepository.findByFullIdentifierAndInstitutionIdAndActionUnitFullIdentifier(
                key.fullIdentifier, institutionId, key.actionIdentifier())
                .orElseThrow(() -> new IllegalStateException("Recording unit introuvable"));
    }

    /**
     * Bulk variant of {@link #getRecordingUnitFromKey} — one query per distinct action unit rather
     * than one per key. Missing keys are simply absent from the returned map (callers decide how to
     * report that, matching how {@code getRecordingUnitFromKey} throws for a single missing key).
     */
    public Map<RecordingUnitKey, RecordingUnit> bulkGetRecordingUnitsFromKeys(Collection<RecordingUnitKey> keys, Long institutionId) {
        Map<String, List<String>> fullIdsByActionIdentifier = keys.stream()
                .collect(Collectors.groupingBy(RecordingUnitKey::actionIdentifier,
                        Collectors.mapping(RecordingUnitKey::fullIdentifier, Collectors.toList())));
        Map<RecordingUnitKey, RecordingUnit> result = new HashMap<>();
        for (var entry : fullIdsByActionIdentifier.entrySet()) {
            for (RecordingUnit ru : recordingUnitRepository.findAllByFullIdentifierInAndInstitutionIdAndActionUnitFullIdentifier(
                    entry.getValue(), institutionId, entry.getKey())) {
                result.put(new RecordingUnitKey(ru.getFullIdentifier(), entry.getKey()), ru);
            }
        }
        return result;
    }

    // -------------------------------------------------------------------------
    // Bulk seeding: collect distinct lookup keys, fetch each reference type in a
    // handful of queries instead of per-row, then build + batch-write with periodic
    // flush+clear to bound the persistence context for large imports.
    // -------------------------------------------------------------------------

    public void seed(List<RecordingUnitSpecs> specs) {
        seed(specs, new ImportProgress());
    }

    public void seed(List<RecordingUnitSpecs> specs, ImportProgress progress) {
        if (specs.isEmpty()) return;

        Map<String, Institution> institutionsByIdentifier = fetchInstitutions(specs);
        Map<String, Person> personCache = prefetchPersons(specs);
        Map<ConceptSeeder.ConceptKey, Concept> conceptsByKey = fetchConcepts(specs);
        Map<ActionUnitSeeder.ActionUnitKey, ActionUnit> actionUnitsByKey = fetchActionUnits(specs);
        Map<SpatialUnitSeeder.SpatialUnitKey, SpatialUnit> spatialUnitsByKey = fetchSpatialUnits(specs, institutionsByIdentifier);
        Map<String, Phase> phasesByCompositeKey = fetchPhases(specs, actionUnitsByKey);
        Map<RecordingUnitKey, Boolean> existingKeys = fetchExistingRecordingUnits(specs, institutionsByIdentifier, actionUnitsByKey);

        List<RecordingUnit> toInsert = new ArrayList<>();
        for (int i = 0; i < specs.size(); i++) {
            var s = specs.get(i);
            try {
                RecordingUnit built = buildRecordingUnit(s, institutionsByIdentifier, personCache, conceptsByKey,
                        actionUnitsByKey, spatialUnitsByKey, phasesByCompositeKey);
                RecordingUnitKey key = new RecordingUnitKey(s.fullIdentifier(), built.getActionUnit().getFullIdentifier());
                if (existingKeys.putIfAbsent(key, Boolean.TRUE) == null) {
                    toInsert.add(built);
                }
            } catch (Exception e) {
                throw new IllegalStateException(
                        "[UE ligne " + (i + 1) + "] '" + s.fullIdentifier() + "' : " + e.getMessage(), e);
            }
        }

        for (int i = 0; i < toInsert.size(); i += FLUSH_CHUNK_SIZE) {
            List<RecordingUnit> chunk = toInsert.subList(i, Math.min(i + FLUSH_CHUNK_SIZE, toInsert.size()));
            recordingUnitRepository.saveAll(chunk);
            entityManager.flush();
            entityManager.clear();
            progress.advance(chunk.size());
        }
        // specs that were skipped as already-existing never went into toInsert, so they'd otherwise
        // never be accounted for in the running total — advance for them too so the overall import
        // progress (summed across all 6 seeders in ProjectDataSeeder) still reaches exactly 100%.
        progress.advance(specs.size() - toInsert.size());
    }

    private RecordingUnit buildRecordingUnit(RecordingUnitSpecs s, Map<String, Institution> institutionsByIdentifier,
                                              Map<String, Person> personCache, Map<ConceptSeeder.ConceptKey, Concept> conceptsByKey,
                                              Map<ActionUnitSeeder.ActionUnitKey, ActionUnit> actionUnitsByKey,
                                              Map<SpatialUnitSeeder.SpatialUnitKey, SpatialUnit> spatialUnitsByKey,
                                              Map<String, Phase> phasesByCompositeKey) {
        Concept type           = SeederUtils.field("type",                  () -> resolveConcept(conceptsByKey, s.type));
        Concept geoCycle       = s.geomorphologicalCycle  != null ? SeederUtils.field("geomorphologicalCycle",  () -> resolveConcept(conceptsByKey, s.geomorphologicalCycle))  : null;
        Concept geoAgent       = s.geomorphologicalAgent  != null ? SeederUtils.field("geomorphologicalAgent",  () -> resolveConcept(conceptsByKey, s.geomorphologicalAgent))  : null;
        Concept interpretation = s.interpretation         != null ? SeederUtils.field("interpretation",         () -> resolveConcept(conceptsByKey, s.interpretation))         : null;

        Institution institution = SeederUtils.field("institutionIdentifier", () -> {
            Institution inst = institutionsByIdentifier.get(s.institutionIdentifier);
            if (inst == null) throw new IllegalStateException("Institution introuvable");
            return inst;
        });

        Person authorPerson = SeederUtils.field("author",    () -> personSeeder.resolveCached(personCache, s.author));
        Person createdBy    = SeederUtils.field("createdBy", () -> personSeeder.resolveCached(personCache, s.createdBy));

        List<Person> contributors = new ArrayList<>();
        if (s.excavators != null) {
            for (var email : s.excavators) {
                contributors.add(SeederUtils.field("excavators[" + email + "]", () -> personSeeder.resolveCached(personCache, email)));
            }
        }

        SpatialUnit su = null;
        if (s.spatialUnitName != null) {
            su = SeederUtils.field("spatialUnitName", () -> {
                SpatialUnit found = spatialUnitsByKey.get(s.spatialUnitName);
                if (found == null) throw new IllegalStateException("Lieu " + s.spatialUnitName.unitName() + " introuvable");
                return found;
            });
        }
        ActionUnit au = SeederUtils.field("actionUnitIdentifier", () -> {
            ActionUnit found = actionUnitsByKey.get(s.actionUnitIdentifier);
            if (found == null) throw new IllegalStateException("Action introuvable");
            return found;
        });

        RecordingUnit toGetOrCreate = new RecordingUnit();
        toGetOrCreate.setCreatedByInstitution(institution);
        toGetOrCreate.setDescription(s.description);
        toGetOrCreate.setMatrixTexture(s.matrixTexture);
        toGetOrCreate.setMatrixComposition(s.matrixComposition);
        toGetOrCreate.setMatrixColor(s.matrixColor);
        toGetOrCreate.setIdentifier(s.identifier);
        toGetOrCreate.setFullIdentifier(s.fullIdentifier);
        toGetOrCreate.setType(type);
        toGetOrCreate.setGeomorphologicalAgent(geoAgent);
        toGetOrCreate.setGeomorphologicalCycle(geoCycle);
        toGetOrCreate.setNormalizedInterpretation(interpretation);
        toGetOrCreate.setOpeningDate(s.beginDate);
        toGetOrCreate.setAuthor(authorPerson);
        toGetOrCreate.setContributors(contributors);
        toGetOrCreate.setCreatedBy(createdBy);
        toGetOrCreate.setClosingDate(s.endDate);
        toGetOrCreate.setCreationTime(s.creationTime);
        toGetOrCreate.setActionUnit(au);
        toGetOrCreate.setSpatialUnit(su);

        if (s.phaseIdentifiers != null && !s.phaseIdentifiers.isEmpty()) {
            Set<Phase> phases = new HashSet<>();
            for (String phaseId : s.phaseIdentifiers) {
                Phase p = phasesByCompositeKey.get(phaseCompositeKey(au.getId(), phaseId));
                if (p != null) phases.add(p);
            }
            toGetOrCreate.setPhases(phases);
        }

        return toGetOrCreate;
    }

    private Concept resolveConcept(Map<ConceptSeeder.ConceptKey, Concept> cache, ConceptSeeder.ConceptKey key) {
        Concept c = cache.get(key);
        if (c == null) throw new IllegalStateException("Concept " + key + " introuvable");
        return c;
    }

    private String phaseCompositeKey(Long actionUnitId, String phaseIdentifier) {
        return actionUnitId + "|" + phaseIdentifier;
    }

    private Map<String, Institution> fetchInstitutions(List<RecordingUnitSpecs> specs) {
        Set<String> identifiers = specs.stream().map(RecordingUnitSpecs::institutionIdentifier)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        if (identifiers.isEmpty()) return Map.of();
        return institutionRepository.findAllByIdentifierIn(identifiers).stream()
                .collect(Collectors.toMap(Institution::getIdentifier, i -> i, (a, b) -> a));
    }

    private Map<String, Person> prefetchPersons(List<RecordingUnitSpecs> specs) {
        List<String> nameLastNameStrings = new ArrayList<>();
        for (RecordingUnitSpecs s : specs) {
            nameLastNameStrings.add(s.author());
            nameLastNameStrings.add(s.createdBy());
            if (s.excavators() != null) nameLastNameStrings.addAll(s.excavators());
        }
        return personSeeder.prefetchByNameLastName(nameLastNameStrings);
    }

    private Map<ConceptSeeder.ConceptKey, Concept> fetchConcepts(List<RecordingUnitSpecs> specs) {
        Map<String, Set<String>> lowerIdcsByVocab = new HashMap<>();
        for (RecordingUnitSpecs s : specs) {
            addConceptKey(lowerIdcsByVocab, s.type());
            addConceptKey(lowerIdcsByVocab, s.geomorphologicalCycle());
            addConceptKey(lowerIdcsByVocab, s.geomorphologicalAgent());
            addConceptKey(lowerIdcsByVocab, s.interpretation());
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

    private Map<ActionUnitSeeder.ActionUnitKey, ActionUnit> fetchActionUnits(List<RecordingUnitSpecs> specs) {
        Set<ActionUnitSeeder.ActionUnitKey> keys = specs.stream().map(RecordingUnitSpecs::actionUnitIdentifier)
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

    private Map<SpatialUnitSeeder.SpatialUnitKey, SpatialUnit> fetchSpatialUnits(List<RecordingUnitSpecs> specs,
                                                                                  Map<String, Institution> institutionsByIdentifier) {
        Map<Long, List<String>> namesByInstitutionId = new HashMap<>();
        Map<Long, String> upperNameToOriginal = new HashMap<>(); // not needed but kept simple below via direct key match
        for (RecordingUnitSpecs s : specs) {
            if (s.spatialUnitName() == null) continue;
            Institution inst = institutionsByIdentifier.get(s.institutionIdentifier());
            if (inst == null) continue; // will surface as "Institution introuvable" during build
            namesByInstitutionId.computeIfAbsent(inst.getId(), k -> new ArrayList<>()).add(s.spatialUnitName().unitName().toUpperCase());
        }
        Map<SpatialUnitSeeder.SpatialUnitKey, SpatialUnit> result = new HashMap<>();
        for (var entry : namesByInstitutionId.entrySet()) {
            for (SpatialUnit su : spatialUnitRepository.findAllByNameInAndInstitution(entry.getValue(), entry.getKey())) {
                result.put(new SpatialUnitSeeder.SpatialUnitKey(su.getName()), su);
            }
        }
        return result;
    }

    private Map<String, Phase> fetchPhases(List<RecordingUnitSpecs> specs, Map<ActionUnitSeeder.ActionUnitKey, ActionUnit> actionUnitsByKey) {
        Map<Long, List<String>> phaseIdsByActionUnitId = new HashMap<>();
        for (RecordingUnitSpecs s : specs) {
            if (s.phaseIdentifiers() == null || s.phaseIdentifiers().isEmpty() || s.actionUnitIdentifier() == null) continue;
            ActionUnit au = actionUnitsByKey.get(s.actionUnitIdentifier());
            if (au == null) continue; // will surface as "Action introuvable" during build
            phaseIdsByActionUnitId.computeIfAbsent(au.getId(), k -> new ArrayList<>()).addAll(s.phaseIdentifiers());
        }
        Map<String, Phase> result = new HashMap<>();
        for (var entry : phaseIdsByActionUnitId.entrySet()) {
            for (Phase p : phaseRepository.findAllByIdentifierInAndActionUnitId(entry.getValue(), entry.getKey())) {
                result.put(phaseCompositeKey(entry.getKey(), p.getIdentifier()), p);
            }
        }
        return result;
    }

    private record InstitutionActionKey(Long institutionId, String actionUnitFullIdentifier) {}

    private Map<RecordingUnitKey, Boolean> fetchExistingRecordingUnits(List<RecordingUnitSpecs> specs,
                                                                        Map<String, Institution> institutionsByIdentifier,
                                                                        Map<ActionUnitSeeder.ActionUnitKey, ActionUnit> actionUnitsByKey) {
        // group by (institutionId, actionUnitFullIdentifier) since both are needed for the exact-match query
        Map<InstitutionActionKey, List<String>> fullIdsByInstitutionAndAction = new HashMap<>();
        for (RecordingUnitSpecs s : specs) {
            Institution inst = institutionsByIdentifier.get(s.institutionIdentifier());
            ActionUnit au = s.actionUnitIdentifier() != null ? actionUnitsByKey.get(s.actionUnitIdentifier()) : null;
            if (inst == null || au == null) continue; // will surface as an error during build
            fullIdsByInstitutionAndAction.computeIfAbsent(new InstitutionActionKey(inst.getId(), au.getFullIdentifier()), k -> new ArrayList<>())
                    .add(s.fullIdentifier());
        }
        Map<RecordingUnitKey, Boolean> result = new HashMap<>();
        for (var entry : fullIdsByInstitutionAndAction.entrySet()) {
            Long institutionId = entry.getKey().institutionId();
            String actionUnitFullIdentifier = entry.getKey().actionUnitFullIdentifier();
            for (RecordingUnit ru : recordingUnitRepository.findAllByFullIdentifierInAndInstitutionIdAndActionUnitFullIdentifier(
                    entry.getValue(), institutionId, actionUnitFullIdentifier)) {
                result.put(new RecordingUnitKey(ru.getFullIdentifier(), actionUnitFullIdentifier), Boolean.TRUE);
            }
        }
        return result;
    }
}
