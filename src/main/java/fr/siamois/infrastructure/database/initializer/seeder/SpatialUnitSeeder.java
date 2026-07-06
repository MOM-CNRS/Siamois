package fr.siamois.infrastructure.database.initializer.seeder;


import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.institution.Institution;
import fr.siamois.domain.models.spatialunit.SpatialUnit;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.infrastructure.database.repositories.SpatialUnitRepository;
import fr.siamois.infrastructure.database.repositories.institution.InstitutionRepository;
import fr.siamois.infrastructure.database.repositories.vocabulary.ConceptRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;


@Service
@RequiredArgsConstructor

public class SpatialUnitSeeder {
    private final PersonSeeder personSeeder;
    private final InstitutionRepository institutionRepository;
    private final ConceptRepository conceptRepository;
    private final SpatialUnitRepository spatialUnitRepository;


    public record SpatialUnitSpecs(String name, String typeVocabularyExtId, String typeConceptExtId, String authorEmail,
                                   String institutionIdentifier, Set<SpatialUnitKey> childrenKey) {



    }

    public record SpatialUnitKey(String unitName) {}

    public SpatialUnit findSpatialUnitOrNull(String name, long institutionId) {
        Optional<SpatialUnit> opt = spatialUnitRepository.findByNameAndInstitution(name, institutionId);
        return opt.orElse(null);
    }

    // -------------------------------------------------------------------------
    // Bulk seeding: collect distinct lookup keys, fetch each reference type in a
    // handful of queries instead of per-row, then build in two passes (entities,
    // then self-referential children — a parent can reference another spec from
    // this very same batch, so children can only be resolved once every entity in
    // the batch has been built) and write with a single saveAll. Unlike
    // RecordingUnitSeeder, this is NOT chunked with periodic flush+clear: a parent
    // built in a later chunk could hold a reference to a child built in an earlier
    // one, and clearing the persistence context between chunks would detach that
    // child, breaking the save.
    // -------------------------------------------------------------------------

    public Map<String, SpatialUnit> seed(List<SpatialUnitSpecs> specs) {
        if (specs.isEmpty()) return Map.of();

        Map<String, Institution> institutionsByIdentifier = fetchInstitutions(specs);
        Map<String, Person> personCache = personSeeder.prefetchByNameLastName(
                specs.stream().map(SpatialUnitSpecs::authorEmail).toList());
        Map<ConceptSeeder.ConceptKey, Concept> conceptsByKey = fetchConcepts(specs);

        Map<String, SpatialUnit> builtByName = buildEntities(specs, institutionsByIdentifier, personCache, conceptsByKey);
        Map<String, SpatialUnit> existingByKey = fetchExistingSpatialUnits(specs, institutionsByIdentifier);

        Map<String, SpatialUnit> result = new LinkedHashMap<>();
        List<SpatialUnit> toInsert = new ArrayList<>();
        Set<String> queuedNames = new HashSet<>();
        for (int i = 0; i < specs.size(); i++) {
            resolveSpatialUnit(specs.get(i), i, builtByName, existingByKey, institutionsByIdentifier, queuedNames, toInsert, result);
        }

        if (!toInsert.isEmpty()) {
            spatialUnitRepository.saveAll(toInsert);
            SeederUtils.logBatch("SpatialUnitSeeder", toInsert.size(), toInsert.size(), toInsert.size());
        }

        return result;
    }

    private Map<String, SpatialUnit> buildEntities(List<SpatialUnitSpecs> specs, Map<String, Institution> institutionsByIdentifier,
                                                     Map<String, Person> personCache, Map<ConceptSeeder.ConceptKey, Concept> conceptsByKey) {
        Map<String, SpatialUnit> builtByName = new LinkedHashMap<>();
        for (int i = 0; i < specs.size(); i++) {
            var s = specs.get(i);
            try {
                builtByName.put(s.name(), buildSpatialUnit(s, institutionsByIdentifier, personCache, conceptsByKey));
            } catch (Exception e) {
                throw new IllegalStateException(
                        "[Lieu Ligne " + (i + 1) + "] '" + s.name() + "' : " + e.getMessage(), e);
            }
        }
        return builtByName;
    }

    private SpatialUnit buildSpatialUnit(SpatialUnitSpecs s, Map<String, Institution> institutionsByIdentifier,
                                          Map<String, Person> personCache, Map<ConceptSeeder.ConceptKey, Concept> conceptsByKey) {
        Concept type = SeederUtils.field("type", () -> {
            Concept c = conceptsByKey.get(new ConceptSeeder.ConceptKey(s.typeVocabularyExtId(), s.typeConceptExtId()));
            if (c == null) throw new IllegalStateException("Concept introuvable");
            return c;
        });
        Person author = SeederUtils.field("auteur", () -> personSeeder.resolveCached(personCache, s.authorEmail()));
        Institution institution = SeederUtils.field("institutionIdentifier", () -> {
            Institution inst = institutionsByIdentifier.get(s.institutionIdentifier());
            if (inst == null) throw new IllegalStateException("Institution introuvable");
            return inst;
        });

        SpatialUnit toGetOrCreate = new SpatialUnit();
        toGetOrCreate.setName(s.name());
        toGetOrCreate.setCreatedByInstitution(institution);
        toGetOrCreate.setCreatedBy(author);
        toGetOrCreate.setCategory(type);
        return toGetOrCreate;
    }

    private void resolveSpatialUnit(SpatialUnitSpecs s, int index, Map<String, SpatialUnit> builtByName,
                                     Map<String, SpatialUnit> existingByKey, Map<String, Institution> institutionsByIdentifier,
                                     Set<String> queuedNames, List<SpatialUnit> toInsert, Map<String, SpatialUnit> result) {
        try {
            SpatialUnit built = builtByName.get(s.name());
            Set<SpatialUnit> children = SeederUtils.field("childrenKey", () -> resolveChildren(builtByName, s.childrenKey()));
            built.setChildren(children);

            SpatialUnit existing = findExistingSpatialUnit(s, institutionsByIdentifier, existingByKey);
            if (existing != null) {
                result.put(s.name(), existing);
                return;
            }
            // multiple spec rows can share the same name (last-write-wins in builtByName above) —
            // only queue the built entity once, or saveAll would insert the same object twice
            if (queuedNames.add(s.name())) {
                toInsert.add(built);
            }
            result.put(s.name(), built);
        } catch (Exception e) {
            throw new IllegalStateException(
                    "[Lieu Ligne " + (index + 1) + "] '" + s.name() + "' : " + e.getMessage(), e);
        }
    }

    private SpatialUnit findExistingSpatialUnit(SpatialUnitSpecs s, Map<String, Institution> institutionsByIdentifier,
                                                 Map<String, SpatialUnit> existingByKey) {
        Institution institution = institutionsByIdentifier.get(s.institutionIdentifier());
        return institution != null ? existingByKey.get(existingDedupKey(institution.getId(), s.name())) : null;
    }

    private Set<SpatialUnit> resolveChildren(Map<String, SpatialUnit> builtByName, Set<SpatialUnitKey> childrenKeys) {
        Set<SpatialUnit> children = new HashSet<>();
        if (childrenKeys != null) {
            for (var childKey : childrenKeys) {
                SpatialUnit child = builtByName.get(childKey.unitName());
                if (child == null) throw new IllegalStateException("Enfant introuvable");
                children.add(child);
            }
        }
        return children;
    }

    private Map<String, Institution> fetchInstitutions(List<SpatialUnitSpecs> specs) {
        Set<String> identifiers = specs.stream().map(SpatialUnitSpecs::institutionIdentifier)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        if (identifiers.isEmpty()) return Map.of();
        return institutionRepository.findAllByIdentifierIn(identifiers).stream()
                .collect(Collectors.toMap(Institution::getIdentifier, i -> i, (a, b) -> a));
    }

    private Map<ConceptSeeder.ConceptKey, Concept> fetchConcepts(List<SpatialUnitSpecs> specs) {
        Map<String, Set<String>> lowerIdcsByVocab = new HashMap<>();
        for (SpatialUnitSpecs s : specs) {
            if (s.typeVocabularyExtId() == null || s.typeConceptExtId() == null) continue;
            lowerIdcsByVocab.computeIfAbsent(s.typeVocabularyExtId(), k -> new HashSet<>()).add(s.typeConceptExtId().toLowerCase());
        }
        Map<ConceptSeeder.ConceptKey, Concept> result = new HashMap<>();
        for (var entry : lowerIdcsByVocab.entrySet()) {
            for (Concept c : conceptRepository.findAllByExternalVocabularyIdIgnoreCaseAndExternalIdIgnoreCaseIn(entry.getKey(), entry.getValue())) {
                result.put(new ConceptSeeder.ConceptKey(entry.getKey(), c.getExternalId()), c);
            }
        }
        return result;
    }

    private Map<String, SpatialUnit> fetchExistingSpatialUnits(List<SpatialUnitSpecs> specs, Map<String, Institution> institutionsByIdentifier) {
        Map<Long, List<String>> namesByInstitutionId = new HashMap<>();
        for (SpatialUnitSpecs s : specs) {
            Institution inst = institutionsByIdentifier.get(s.institutionIdentifier());
            if (inst == null) continue;
            namesByInstitutionId.computeIfAbsent(inst.getId(), k -> new ArrayList<>()).add(s.name().toUpperCase());
        }
        Map<String, SpatialUnit> result = new HashMap<>();
        for (var entry : namesByInstitutionId.entrySet()) {
            for (SpatialUnit su : spatialUnitRepository.findAllByNameInAndInstitution(entry.getValue(), entry.getKey())) {
                result.put(existingDedupKey(entry.getKey(), su.getName()), su);
            }
        }
        return result;
    }

    private String existingDedupKey(Long institutionId, String name) {
        return institutionId + "|" + name.toUpperCase();
    }
}
