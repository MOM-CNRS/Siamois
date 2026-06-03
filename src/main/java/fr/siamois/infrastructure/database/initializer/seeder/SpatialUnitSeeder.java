package fr.siamois.infrastructure.database.initializer.seeder;


import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.institution.Institution;
import fr.siamois.domain.models.spatialunit.SpatialUnit;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.infrastructure.database.repositories.SpatialUnitRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;


@Service
@RequiredArgsConstructor

public class SpatialUnitSeeder {
    private final PersonSeeder personSeeder;
    private final ConceptSeeder conceptSeeder;
    private final InstitutionSeeder institutionSeeder;
    private final SpatialUnitRepository spatialUnitRepository;


    public record SpatialUnitSpecs(String name, String typeVocabularyExtId, String typeConceptExtId, String authorEmail,
                                   String institutionIdentifier, Set<SpatialUnitKey> childrenKey) {



    }

    public record SpatialUnitKey(String unitName) {}

    private SpatialUnit getOrCreateSpatialUnit(SpatialUnit spatialUnit) {

        Optional<SpatialUnit> opt = spatialUnitRepository.findByNameAndInstitution(spatialUnit.getName(), spatialUnit.getCreatedByInstitution().getId());

        return opt.orElseGet(() -> spatialUnitRepository.save(spatialUnit));
    }

    public SpatialUnit findSpatialUnitOrNull(String name, long institutionId) {
        Optional<SpatialUnit> opt = spatialUnitRepository.findByNameAndInstitution(name, institutionId);
        return opt.orElse(null);
    }

    private Set<SpatialUnit> initializeChildren(long institutionId, Set<SpatialUnitKey> childrenKeys) {
        Set<SpatialUnit> children = new HashSet<>();
        if(childrenKeys != null) {
            for(var childKey : childrenKeys) {
                SpatialUnit child = findSpatialUnitOrNull(childKey.unitName, institutionId);
                if(child == null) {
                    throw new IllegalStateException("Enfant introuvable");
                }
                children.add(child);
            }
        }
        return children;
    }

    public Map<String, SpatialUnit> seed(List<SpatialUnitSpecs> specs) {
        Map<String, SpatialUnit> result = new HashMap<>();
        for (int i = 0; i < specs.size(); i++) {
            var s = specs.get(i);
            try {
            Concept type = SeederUtils.field("type", () -> {
                Concept c = conceptSeeder.findConceptOrReturnNull(s.typeVocabularyExtId, s.typeConceptExtId);
                if (c == null) throw new IllegalStateException("Concept introuvable");
                return c;
            });
            Person author = SeederUtils.field("authorEmail", () -> {
                Person p = personSeeder.findPersonOrReturnNull(s.authorEmail);
                if (p == null) throw new IllegalStateException("Auteur introuvable");
                return p;
            });
            Institution institution = SeederUtils.field("institutionIdentifier", () -> {
                Institution inst = institutionSeeder.findInstitutionOrReturnNull(s.institutionIdentifier);
                if (inst == null) throw new IllegalStateException("Institution introuvable");
                return inst;
            });
            Set<SpatialUnit> children = SeederUtils.field("childrenKey", () -> initializeChildren(institution.getId(), s.childrenKey));

            SpatialUnit toGetOrCreate = new SpatialUnit();
            toGetOrCreate.setName(s.name);
            toGetOrCreate.setCreatedByInstitution(institution);
            toGetOrCreate.setCreatedBy(author);
            toGetOrCreate.setCategory(type);
            toGetOrCreate.setChildren(children);

            result.put(s.name, getOrCreateSpatialUnit(toGetOrCreate));

            } catch (Exception e) {
                throw new IllegalStateException(
                        "[Lieu Ligne " + (i + 1) + "] '" + s.name + "' : " + e.getMessage(), e);
            }
        }
        return result;
    }
}
