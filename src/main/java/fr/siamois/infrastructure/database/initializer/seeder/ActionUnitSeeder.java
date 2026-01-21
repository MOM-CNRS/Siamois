package fr.siamois.infrastructure.database.initializer.seeder;


import fr.siamois.domain.models.actionunit.ActionCode;
import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.institution.Institution;
import fr.siamois.domain.models.spatialunit.SpatialUnit;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.infrastructure.database.repositories.SpatialUnitRepository;
import fr.siamois.infrastructure.database.repositories.actionunit.ActionCodeRepository;
import fr.siamois.infrastructure.database.repositories.actionunit.ActionUnitRepository;
import fr.siamois.infrastructure.database.repositories.institution.InstitutionRepository;
import fr.siamois.infrastructure.database.repositories.person.PersonRepository;
import fr.siamois.infrastructure.database.repositories.vocabulary.ConceptRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;


@Service
@RequiredArgsConstructor
public class ActionUnitSeeder {
    private final PersonRepository personRepository;
    private final ActionCodeRepository actionCodeRepository;
    private final ConceptRepository conceptRepository;
    private final InstitutionRepository institutionRepository;
    private final SpatialUnitRepository spatialUnitRepository;
    private final ActionUnitRepository  actionUnitRepository;


    public record ActionUnitSpecs(String fullIdentifier, String name, String identifier, String primaryActionCode,
                                  String typeVocabularyExtId, String typeConceptExtId,
                                  String authorEmail,
                                  String institutionIdentifier, OffsetDateTime beginDate, OffsetDateTime endDate,
                                  Set<SpatialUnitSeeder.SpatialUnitKey> spatialContextKeys) {

    }

    public record ActionUnitKey(String fullIdentifier) {}

    private void getOrCreateActionUnit(ActionUnit actionUnit) {
        Optional<ActionUnit> opt = actionUnitRepository.findByFullIdentifier(actionUnit.getFullIdentifier());
        if (opt.isEmpty()) {
            actionUnitRepository.save(actionUnit);
        }
    }

    public void seed(List<ActionUnitSpecs> specs) {

        for (var s : specs) {
            // Find Type
            Concept type = conceptRepository
                    .findConceptByExternalIdIgnoreCase(s.typeVocabularyExtId, s.typeConceptExtId)
                    .orElseThrow(() -> new IllegalStateException("Concept introuvable"));
            // Find author
            Person author = personRepository
                    .findByEmailIgnoreCase(s.authorEmail)
                    .orElseThrow(() -> new IllegalStateException("Auteur introuvable"));
            // Find action code
            ActionCode actionCode = null;
            if(s.primaryActionCode!=null) {
                actionCode = actionCodeRepository
                        .findById(s.primaryActionCode)
                        .orElseThrow(() -> new IllegalStateException("Action code introuvable"));
            }

            // Find Institution
            Institution institution = institutionRepository.findInstitutionByIdentifier(s.institutionIdentifier)
                    .orElseThrow(() -> new IllegalStateException("Institution introuvable"));
            Set<SpatialUnit> spatialContext = new HashSet<>();
            if(s.spatialContextKeys != null) {
                for(var childKey : s.spatialContextKeys) {
                    SpatialUnit child = spatialUnitRepository.findByNameAndInstitution(childKey.unitName(), institution.getId())
                            .orElseThrow(() -> new IllegalStateException("USp introuvable"));
                    spatialContext.add(child);
                }
            }

            ActionUnit toGetOrCreate = new ActionUnit();
            toGetOrCreate.setCreatedByInstitution(institution);
            toGetOrCreate.setIdentifier(s.identifier);
            toGetOrCreate.setName(s.name);
            toGetOrCreate.setCreatedBy(author);
            if(actionCode!=null) {
                toGetOrCreate.setPrimaryActionCode(actionCode);
            }

            toGetOrCreate.setFullIdentifier(s.fullIdentifier);
            toGetOrCreate.setType(type);
            toGetOrCreate.setSpatialContext(spatialContext);
            toGetOrCreate.setBeginDate(s.beginDate);
            toGetOrCreate.setEndDate(s.endDate);
            getOrCreateActionUnit(toGetOrCreate);

        }
    }
}
