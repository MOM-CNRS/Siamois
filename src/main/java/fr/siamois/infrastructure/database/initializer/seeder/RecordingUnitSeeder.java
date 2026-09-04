package fr.siamois.infrastructure.database.initializer.seeder;


import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.institution.Institution;
import fr.siamois.domain.models.misc.ImportProgress;
import fr.siamois.domain.models.misc.SeedCounts;
import fr.siamois.domain.models.phase.Phase;
import fr.siamois.domain.models.recordingunit.RecordingUnit;
import fr.siamois.domain.models.spatialunit.SpatialUnit;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.infrastructure.dataimport.ImportSchema;
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
    private final ConceptSeeder conceptSeeder;

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
                                     List<String> phaseIdentifiers,
                                     String comments,
                                     Integer taq,
                                     Integer tpq,
                                     ConceptSeeder.ConceptKey erosionShape,
                                     ConceptSeeder.ConceptKey erosionOrientation,
                                     ConceptSeeder.ConceptKey erosionProfile,
                                     ConceptSeeder.ConceptKey chronologicalAttribution,
                                     Integer excelRowNumber) {

    }

    public record RecordingUnitKey(String fullIdentifier, String actionIdentifier) {
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
        seed(specs, progress, new SeedCounts());
    }

    public void seed(List<RecordingUnitSpecs> specs, ImportProgress progress, SeedCounts seedCounts) {
        if (specs.isEmpty()) return;

        Map<String, Institution> institutionsByIdentifier = fetchInstitutions(specs);
        Map<String, Person> personCache = prefetchPersons(specs);
        Map<ConceptSeeder.ConceptKey, Concept> conceptsByKey = fetchConcepts(specs);
        Map<ActionUnitSeeder.ActionUnitKey, ActionUnit> actionUnitsByKey = fetchActionUnits(specs);
        Map<SpatialUnitSeeder.SpatialUnitKey, SpatialUnit> spatialUnitsByKey = fetchSpatialUnits(specs, institutionsByIdentifier);
        Map<String, Phase> phasesByCompositeKey = fetchPhases(specs, actionUnitsByKey);
        Map<RecordingUnitKey, RecordingUnit> existingByKey = fetchExistingRecordingUnits(specs, institutionsByIdentifier, actionUnitsByKey);

        List<RecordingUnit> toInsert = new ArrayList<>();
        List<RecordingUnit> toUpdate = new ArrayList<>();
        Set<RecordingUnitKey> queuedKeys = new HashSet<>();
        for (int i = 0; i < specs.size(); i++) {
            var s = specs.get(i);
            try {
                RecordingUnit built = buildRecordingUnit(s, institutionsByIdentifier, personCache, conceptsByKey,
                        actionUnitsByKey, spatialUnitsByKey, phasesByCompositeKey);
                RecordingUnitKey key = new RecordingUnitKey(s.fullIdentifier(), built.getActionUnit().getFullIdentifier());
                if (!queuedKeys.add(key)) continue; // in-batch duplicate, keep last build already queued
                RecordingUnit existing = existingByKey.get(key);
                if (existing != null) {
                    mergeRecordingUnitInto(built, existing);
                    toUpdate.add(existing);
                } else {
                    toInsert.add(built);
                }
            } catch (Exception e) {
                throw new IllegalStateException(
                        "[UE ligne " + SeederUtils.lineNumber(s.excelRowNumber(), i) + "] '" + s.fullIdentifier() + "' : " + e.getMessage(), e);
            }
        }

        for (int i = 0; i < toInsert.size(); i += FLUSH_CHUNK_SIZE) {
            List<RecordingUnit> chunk = toInsert.subList(i, Math.min(i + FLUSH_CHUNK_SIZE, toInsert.size()));
            recordingUnitRepository.saveAll(chunk);
            entityManager.flush();
            entityManager.clear();
            progress.advance(chunk.size());
            SeederUtils.logBatch("RecordingUnitSeeder", i + chunk.size(), FLUSH_CHUNK_SIZE, toInsert.size());
        }
        for (int i = 0; i < toUpdate.size(); i += FLUSH_CHUNK_SIZE) {
            List<RecordingUnit> chunk = toUpdate.subList(i, Math.min(i + FLUSH_CHUNK_SIZE, toUpdate.size()));
            recordingUnitRepository.saveAll(chunk);
            entityManager.flush();
            entityManager.clear();
            progress.advance(chunk.size());
            SeederUtils.logBatch("RecordingUnitSeeder", i + chunk.size(), FLUSH_CHUNK_SIZE, toUpdate.size());
        }
        // specs skipped as in-batch duplicates never went into toInsert/toUpdate, so they'd otherwise
        // never be accounted for in the running total — advance for them too so the overall import
        // progress (summed across all 6 seeders in ProjectDataSeeder) still reaches exactly 100%.
        progress.advance(specs.size() - toInsert.size() - toUpdate.size());
        seedCounts.record(ImportSchema.RECORDING_UNIT, toInsert.size(), toUpdate.size(),
                specs.size() - toInsert.size() - toUpdate.size());
    }

    private RecordingUnit buildRecordingUnit(RecordingUnitSpecs s, Map<String, Institution> institutionsByIdentifier,
                                              Map<String, Person> personCache, Map<ConceptSeeder.ConceptKey, Concept> conceptsByKey,
                                              Map<ActionUnitSeeder.ActionUnitKey, ActionUnit> actionUnitsByKey,
                                              Map<SpatialUnitSeeder.SpatialUnitKey, SpatialUnit> spatialUnitsByKey,
                                              Map<String, Phase> phasesByCompositeKey) {
        Institution institution = resolveInstitution(s, institutionsByIdentifier);
        Long institutionId = institution.getId();

        Concept type = SeederUtils.field("type", () -> resolveConcept(conceptsByKey, s.type, institutionId));
        Concept geoCycle = resolveOptionalConcept(conceptsByKey, "geomorphologicalCycle", s.geomorphologicalCycle, institutionId);
        Concept geoAgent = resolveOptionalConcept(conceptsByKey, "geomorphologicalAgent", s.geomorphologicalAgent, institutionId);
        Concept interpretation = resolveOptionalConcept(conceptsByKey, "interpretation", s.interpretation, institutionId);
        Concept erosionShape = resolveOptionalConcept(conceptsByKey, "erosionShape", s.erosionShape, institutionId);
        Concept erosionOrientation = resolveOptionalConcept(conceptsByKey, "erosionOrientation", s.erosionOrientation, institutionId);
        Concept erosionProfile = resolveOptionalConcept(conceptsByKey, "erosionProfile", s.erosionProfile, institutionId);
        Concept chronologicalAttribution = resolveOptionalConcept(conceptsByKey, "chronologicalAttribution", s.chronologicalAttribution, institutionId);

        Person authorPerson = SeederUtils.field("author",    () -> personSeeder.resolveCached(personCache, s.author));
        Person createdBy    = SeederUtils.field("createdBy", () -> personSeeder.resolveCached(personCache, s.createdBy));
        List<Person> contributors = resolveContributors(s, personCache);
        SpatialUnit su = resolveSpatialUnit(s, spatialUnitsByKey);
        ActionUnit au = resolveActionUnit(s, actionUnitsByKey);

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
        toGetOrCreate.setComments(s.comments);
        toGetOrCreate.setTaq(s.taq);
        toGetOrCreate.setTpq(s.tpq);
        toGetOrCreate.setErosionShape(erosionShape);
        toGetOrCreate.setErosionOrientation(erosionOrientation);
        toGetOrCreate.setErosionProfile(erosionProfile);
        toGetOrCreate.setChronologicalAttribution(chronologicalAttribution);

        if (s.phaseIdentifiers != null && !s.phaseIdentifiers.isEmpty()) {
            toGetOrCreate.setPhases(resolvePhases(s, au, phasesByCompositeKey));
        }

        return toGetOrCreate;
    }

    private Concept resolveConcept(Map<ConceptSeeder.ConceptKey, Concept> cache, ConceptSeeder.ConceptKey key, Long institutionId) {
        Concept c = cache.get(key);
        if (c == null) throw new IllegalStateException(conceptSeeder.describeMissingConcept(key, institutionId));
        return c;
    }

    private Concept resolveOptionalConcept(Map<ConceptSeeder.ConceptKey, Concept> conceptsByKey, String fieldName, ConceptSeeder.ConceptKey key, Long institutionId) {
        if (key == null) return null;
        return SeederUtils.field(fieldName, () -> resolveConcept(conceptsByKey, key, institutionId));
    }

    private Institution resolveInstitution(RecordingUnitSpecs s, Map<String, Institution> institutionsByIdentifier) {
        return SeederUtils.field("institutionIdentifier", () -> {
            Institution inst = institutionsByIdentifier.get(s.institutionIdentifier);
            if (inst == null) throw new IllegalStateException("Institution introuvable");
            return inst;
        });
    }

    private List<Person> resolveContributors(RecordingUnitSpecs s, Map<String, Person> personCache) {
        List<Person> contributors = new ArrayList<>();
        if (s.excavators == null) return contributors;
        for (var email : s.excavators) {
            contributors.add(SeederUtils.field("excavators[" + email + "]", () -> personSeeder.resolveCached(personCache, email)));
        }
        return contributors;
    }

    private SpatialUnit resolveSpatialUnit(RecordingUnitSpecs s, Map<SpatialUnitSeeder.SpatialUnitKey, SpatialUnit> spatialUnitsByKey) {
        if (s.spatialUnitName == null) return null;
        return SeederUtils.field("spatialUnitName", () -> {
            SpatialUnit found = spatialUnitsByKey.get(s.spatialUnitName);
            if (found == null) throw new IllegalStateException("Lieu " + s.spatialUnitName.unitName() + " introuvable");
            return found;
        });
    }

    private ActionUnit resolveActionUnit(RecordingUnitSpecs s, Map<ActionUnitSeeder.ActionUnitKey, ActionUnit> actionUnitsByKey) {
        return SeederUtils.field("actionUnitIdentifier", () -> {
            ActionUnit found = actionUnitsByKey.get(s.actionUnitIdentifier);
            if (found == null) throw new IllegalStateException("Action introuvable");
            return found;
        });
    }

    private Set<Phase> resolvePhases(RecordingUnitSpecs s, ActionUnit au, Map<String, Phase> phasesByCompositeKey) {
        Set<Phase> phases = new HashSet<>();
        for (String phaseId : s.phaseIdentifiers) {
            Phase p = phasesByCompositeKey.get(phaseCompositeKey(au.getId(), phaseId));
            if (p != null) phases.add(p);
        }
        return phases;
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
            addConceptKey(lowerIdcsByVocab, s.erosionShape());
            addConceptKey(lowerIdcsByVocab, s.erosionOrientation());
            addConceptKey(lowerIdcsByVocab, s.erosionProfile());
            addConceptKey(lowerIdcsByVocab, s.chronologicalAttribution());
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
        for (RecordingUnitSpecs s : specs) {
            if (s.spatialUnitName() == null) continue;
            Institution inst = institutionsByIdentifier.get(s.institutionIdentifier());
            if (inst != null) { // if null, will surface as "Institution introuvable" during build
                namesByInstitutionId.computeIfAbsent(inst.getId(), k -> new ArrayList<>()).add(s.spatialUnitName().unitName().toUpperCase());
            }
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
            if (au != null) { // if null, will surface as "Action introuvable" during build
                phaseIdsByActionUnitId.computeIfAbsent(au.getId(), k -> new ArrayList<>()).addAll(s.phaseIdentifiers());
            }
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

    private Map<RecordingUnitKey, RecordingUnit> fetchExistingRecordingUnits(List<RecordingUnitSpecs> specs,
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
        Map<RecordingUnitKey, RecordingUnit> result = new HashMap<>();
        for (var entry : fullIdsByInstitutionAndAction.entrySet()) {
            Long institutionId = entry.getKey().institutionId();
            String actionUnitFullIdentifier = entry.getKey().actionUnitFullIdentifier();
            for (RecordingUnit ru : recordingUnitRepository.findAllByFullIdentifierInAndInstitutionIdAndActionUnitFullIdentifier(
                    entry.getValue(), institutionId, actionUnitFullIdentifier)) {
                result.put(new RecordingUnitKey(ru.getFullIdentifier(), actionUnitFullIdentifier), ru);
            }
        }
        return result;
    }

    /**
     * Overwrites the content fields of an already-persisted recording unit with those of a
     * freshly-built one from a re-import, so re-importing the same file with corrected values
     * updates the existing row instead of being a no-op. Identity (fullIdentifier/actionUnit) and
     * provenance (createdBy/creationTime) are left untouched.
     */
    private void mergeRecordingUnitInto(RecordingUnit built, RecordingUnit existing) {
        existing.setDescription(built.getDescription());
        existing.setMatrixTexture(built.getMatrixTexture());
        existing.setMatrixComposition(built.getMatrixComposition());
        existing.setMatrixColor(built.getMatrixColor());
        existing.setIdentifier(built.getIdentifier());
        existing.setType(built.getType());
        existing.setGeomorphologicalAgent(built.getGeomorphologicalAgent());
        existing.setGeomorphologicalCycle(built.getGeomorphologicalCycle());
        existing.setNormalizedInterpretation(built.getNormalizedInterpretation());
        existing.setOpeningDate(built.getOpeningDate());
        existing.setAuthor(built.getAuthor());
        existing.setContributors(built.getContributors());
        existing.setClosingDate(built.getClosingDate());
        existing.setSpatialUnit(built.getSpatialUnit());
        existing.setComments(built.getComments());
        existing.setTaq(built.getTaq());
        existing.setTpq(built.getTpq());
        existing.setErosionShape(built.getErosionShape());
        existing.setErosionOrientation(built.getErosionOrientation());
        existing.setErosionProfile(built.getErosionProfile());
        existing.setChronologicalAttribution(built.getChronologicalAttribution());
        if (built.getPhases() != null && !built.getPhases().isEmpty()) {
            existing.setPhases(built.getPhases());
        }
    }
}
