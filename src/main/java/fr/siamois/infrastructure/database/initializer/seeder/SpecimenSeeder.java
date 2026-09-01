package fr.siamois.infrastructure.database.initializer.seeder;

import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.institution.Institution;
import fr.siamois.domain.models.misc.ImportProgress;
import fr.siamois.domain.models.recordingunit.RecordingUnit;
import fr.siamois.domain.models.specimen.Specimen;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.infrastructure.database.repositories.institution.InstitutionRepository;
import fr.siamois.infrastructure.database.repositories.specimen.SpecimenRepository;
import fr.siamois.infrastructure.database.repositories.vocabulary.ConceptRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SpecimenSeeder {

    private static final int FLUSH_CHUNK_SIZE = 100;

    private final InstitutionRepository institutionRepository;
    private final RecordingUnitSeeder recordingUnitSeeder;
    private final SpecimenRepository specimenRepository;
    private final PersonSeeder personSeeder;
    private final ConceptRepository conceptRepository;
    private final ConceptSeeder conceptSeeder;

    @PersistenceContext
    private EntityManager entityManager;

    public record SpecimenSpecs(String fullIdentifier, Integer identifier,
                                     ConceptSeeder.ConceptKey material,
                                     ConceptSeeder.ConceptKey category,
                                     ConceptSeeder.ConceptKey interpretation,
                                     String authorEmail,
                                     String institutionIdentifier,
                                     List<String> authors,
                                     List<String> collectors,
                                     OffsetDateTime creationTime,
                                     RecordingUnitSeeder.RecordingUnitKey recordingUnitKey,
                                     String description,
                                     String comments,
                                     String isolationNumber,
                                     Integer taq,
                                     Integer tpq,
                                     String otherIdentifier,
                                     ConceptSeeder.ConceptKey chronologicalAttribution,
                                     Integer numberOfElements,
                                     OffsetDateTime collectionDate,
                                     ConceptSeeder.ConceptKey collectionMethod,
                                     ConceptSeeder.ConceptKey sanitaryState,
                                     ConceptSeeder.ConceptKey materialClass,
                                     Integer excelRowNumber) {

    }

    private List<Person> buildPersonList(Map<String, Person> personCache, List<String> emails, String fieldPrefix) {
        List<Person> persons = new ArrayList<>();
        if (emails == null) return persons;
        for (var email : emails) {
            persons.add(SeederUtils.field(fieldPrefix + "[" + email + "]", () -> personSeeder.resolveCached(personCache, email)));
        }
        return persons;
    }

    // -------------------------------------------------------------------------
    // Bulk seeding: collect distinct lookup keys, fetch each reference type in a
    // handful of queries instead of per-row, then build + batch-write with periodic
    // flush+clear to bound the persistence context for large imports. Safe to chunk
    // (unlike SpatialUnitSeeder/RecordingUnitRelSeeder) since specimens never
    // reference each other, only externally pre-existing entities.
    // -------------------------------------------------------------------------

    public void seed(List<SpecimenSpecs> specs, Long institutionId) {
        seed(specs, institutionId, new ImportProgress());
    }

    public void seed(List<SpecimenSpecs> specs, Long institutionId, ImportProgress progress) {
        if (specs.isEmpty()) return;

        log.info("[SpecimenSeeder] starting seed of {} specs", specs.size());
        Map<String, Institution> institutionsByIdentifier = fetchInstitutions(specs);
        Map<String, Person> personCache = prefetchPersons(specs);
        Map<ConceptSeeder.ConceptKey, Concept> conceptsByKey = fetchConcepts(specs);
        Map<RecordingUnitSeeder.RecordingUnitKey, RecordingUnit> recordingUnitsByKey =
                recordingUnitSeeder.bulkGetRecordingUnitsFromKeys(
                        specs.stream().map(SpecimenSpecs::recordingUnitKey).filter(Objects::nonNull).collect(Collectors.toSet()),
                        institutionId);
        log.info("[SpecimenSeeder] bulk-fetch done (institutions={}, persons={}, concepts={}, recordingUnits={})",
                institutionsByIdentifier.size(), personCache.size(), conceptsByKey.size(), recordingUnitsByKey.size());

        List<Specimen> built = new ArrayList<>();
        for (int i = 0; i < specs.size(); i++) {
            built.add(buildSpecimen(specs.get(i), i, institutionsByIdentifier, personCache, conceptsByKey, recordingUnitsByKey));
        }

        log.info("[SpecimenSeeder] {} specs built, resolving existing-specimen keys...", built.size());
        Set<String> existingKeys = fetchExistingSpecimenKeys(built);
        log.info("[SpecimenSeeder] existing-key lookup done ({} existing keys found)", existingKeys.size());

        List<Specimen> toInsert = new ArrayList<>();
        Set<String> queuedKeys = new HashSet<>();
        for (Specimen sp : built) {
            String key = dedupKey(sp);
            if (!existingKeys.contains(key) && queuedKeys.add(key)) {
                toInsert.add(sp);
            }
        }
        log.info("[SpecimenSeeder] {} to insert in batches of {}", toInsert.size(), FLUSH_CHUNK_SIZE);

        flushInBatches(toInsert, progress);
        // specs skipped as already-existing or as in-batch duplicates never went into toInsert,
        // so they'd otherwise never be accounted for in the running total.
        progress.advance(specs.size() - toInsert.size());
    }

    private Specimen buildSpecimen(SpecimenSpecs s, int index, Map<String, Institution> institutionsByIdentifier,
                                    Map<String, Person> personCache, Map<ConceptSeeder.ConceptKey, Concept> conceptsByKey,
                                    Map<RecordingUnitSeeder.RecordingUnitKey, RecordingUnit> recordingUnitsByKey) {
        try {
            Institution institution = SeederUtils.field("institutionIdentifier", () -> {
                Institution inst = institutionsByIdentifier.get(s.institutionIdentifier());
                if (inst == null) throw new IllegalStateException("Institution introuvable");
                return inst;
            });
            Long institutionId = institution.getId();

            Concept cat = SeederUtils.field("category", () -> {
                Concept c = conceptsByKey.get(s.category());
                if (c == null) throw new IllegalStateException(conceptSeeder.describeMissingConcept(s.category(), institutionId));
                return c;
            });
            Concept material = resolveOptionalConcept(conceptsByKey, "material", s.material(), institutionId);
            Concept interpretation = resolveOptionalConcept(conceptsByKey, "interpretation", s.interpretation(), institutionId);
            Concept chronologicalAttribution = resolveOptionalConcept(conceptsByKey, "chronologicalAttribution", s.chronologicalAttribution(), institutionId);
            Concept collectionMethod = resolveOptionalConcept(conceptsByKey, "collectionMethod", s.collectionMethod(), institutionId);
            Concept sanitaryState = resolveOptionalConcept(conceptsByKey, "sanitaryState", s.sanitaryState(), institutionId);
            Concept materialClass = resolveOptionalConcept(conceptsByKey, "materialClass", s.materialClass(), institutionId);
            Person author = SeederUtils.field("authorEmail", () -> personSeeder.resolveCached(personCache, s.authorEmail()));

            List<Person> authors    = buildPersonList(personCache, s.authors,    "authors");
            List<Person> collectors = buildPersonList(personCache, s.collectors, "collectors");

            RecordingUnit ru = SeederUtils.field("UE", () -> {
                RecordingUnit found = recordingUnitsByKey.get(s.recordingUnitKey());
                if (found == null) throw new IllegalStateException("Recording unit introuvable");
                return found;
            });

            Specimen toGetOrCreate = new Specimen();
            toGetOrCreate.setCreatedByInstitution(institution);
            toGetOrCreate.setIdentifier(s.identifier);
            toGetOrCreate.setCategory(cat);
            toGetOrCreate.setCreatedBy(author);
            toGetOrCreate.setFullIdentifier(s.fullIdentifier);
            toGetOrCreate.setRecordingUnit(ru);
            toGetOrCreate.setActionUnit(ru.getActionUnit());
            toGetOrCreate.setAuthors(authors);
            toGetOrCreate.setCollectors(collectors);
            toGetOrCreate.setCreationTime(s.creationTime);
            toGetOrCreate.setMaterial(material != null ? Set.of(material) : Set.of());
            toGetOrCreate.setNormalizedInterpretation(interpretation);
            toGetOrCreate.setDescription(s.description());
            toGetOrCreate.setComments(s.comments());
            toGetOrCreate.setIsolationNumber(s.isolationNumber());
            toGetOrCreate.setTaq(s.taq());
            toGetOrCreate.setTpq(s.tpq());
            toGetOrCreate.setOtherIdentifier(s.otherIdentifier());
            toGetOrCreate.setChronologicalAttribution(chronologicalAttribution);
            toGetOrCreate.setNumberOfElements(s.numberOfElements());
            toGetOrCreate.setCollectionDate(s.collectionDate());
            toGetOrCreate.setCollectionMethod(collectionMethod);
            toGetOrCreate.setSanitaryState(sanitaryState);
            toGetOrCreate.setMaterialClass(materialClass != null ? Set.of(materialClass) : Set.of());
            return toGetOrCreate;
        } catch (Exception e) {
            throw new IllegalStateException(
                    "[Spécimen ligne " + SeederUtils.lineNumber(s.excelRowNumber(), index) + "] '" + s.fullIdentifier() + "' : " + e.getMessage(), e);
        }
    }

    private Concept resolveOptionalConcept(Map<ConceptSeeder.ConceptKey, Concept> conceptsByKey, String fieldName, ConceptSeeder.ConceptKey key, Long institutionId) {
        if (key == null) return null;
        return SeederUtils.field(fieldName, () -> {
            Concept c = conceptsByKey.get(key);
            if (c == null) throw new IllegalStateException(conceptSeeder.describeMissingConcept(key, institutionId));
            return c;
        });
    }

    private void flushInBatches(List<Specimen> toInsert, ImportProgress progress) {
        for (int i = 0; i < toInsert.size(); i += FLUSH_CHUNK_SIZE) {
            List<Specimen> chunk = toInsert.subList(i, Math.min(i + FLUSH_CHUNK_SIZE, toInsert.size()));
            specimenRepository.saveAll(chunk);
            entityManager.flush();
            entityManager.clear();
            progress.advance(chunk.size());
            SeederUtils.logBatch("SpecimenSeeder", i + chunk.size(), FLUSH_CHUNK_SIZE, toInsert.size());
        }
    }

    private String dedupKey(Specimen sp) {
        return sp.getCreatedByInstitution().getId() + "|" + sp.getRecordingUnit().getFullIdentifier()
                + "|" + sp.getRecordingUnit().getActionUnit().getFullIdentifier() + "|" + sp.getFullIdentifier();
    }

    /**
     * Grouped by (institution, action unit) only — NOT by recording unit — so this stays a handful of
     * queries even when there are thousands of recording units, each with only one or two specimens.
     * Grouping by recording unit here (as earlier revisions did) degenerates to one query per recording
     * unit for that common shape, which is effectively N+1 again and can silently stall this whole step.
     */
    private Set<String> fetchExistingSpecimenKeys(List<Specimen> built) {
        record GroupKey(Long institutionId, String actionUnitFullIdentifier) {}
        Set<GroupKey> groups = new HashSet<>();
        for (Specimen sp : built) {
            groups.add(new GroupKey(sp.getCreatedByInstitution().getId(), sp.getRecordingUnit().getActionUnit().getFullIdentifier()));
        }
        Set<String> result = new HashSet<>();
        for (GroupKey key : groups) {
            for (SpecimenRepository.ExistingSpecimenKey existing : specimenRepository.findAllKeysByInstitutionIdAndActionUnitFullIdentifier(
                    key.institutionId(), key.actionUnitFullIdentifier())) {
                result.add(key.institutionId() + "|" + existing.getRecordingUnitFullIdentifier() + "|" + key.actionUnitFullIdentifier()
                        + "|" + existing.getFullIdentifier());
            }
        }
        return result;
    }

    private Map<String, Institution> fetchInstitutions(List<SpecimenSpecs> specs) {
        Set<String> identifiers = specs.stream().map(SpecimenSpecs::institutionIdentifier)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        if (identifiers.isEmpty()) return Map.of();
        return institutionRepository.findAllByIdentifierIn(identifiers).stream()
                .collect(Collectors.toMap(Institution::getIdentifier, i -> i, (a, b) -> a));
    }

    private Map<String, Person> prefetchPersons(List<SpecimenSpecs> specs) {
        List<String> nameLastNameStrings = new ArrayList<>();
        for (SpecimenSpecs s : specs) {
            nameLastNameStrings.add(s.authorEmail());
            if (s.authors() != null) nameLastNameStrings.addAll(s.authors());
            if (s.collectors() != null) nameLastNameStrings.addAll(s.collectors());
        }
        return personSeeder.prefetchByNameLastName(nameLastNameStrings);
    }

    private Map<ConceptSeeder.ConceptKey, Concept> fetchConcepts(List<SpecimenSpecs> specs) {
        Map<String, Set<String>> lowerIdcsByVocab = new HashMap<>();
        for (SpecimenSpecs s : specs) {
            addConceptKey(lowerIdcsByVocab, s.category());
            addConceptKey(lowerIdcsByVocab, s.material());
            addConceptKey(lowerIdcsByVocab, s.interpretation());
            addConceptKey(lowerIdcsByVocab, s.chronologicalAttribution());
            addConceptKey(lowerIdcsByVocab, s.collectionMethod());
            addConceptKey(lowerIdcsByVocab, s.sanitaryState());
            addConceptKey(lowerIdcsByVocab, s.materialClass());
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
}
