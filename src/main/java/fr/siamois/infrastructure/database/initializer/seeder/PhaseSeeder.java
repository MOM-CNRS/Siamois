package fr.siamois.infrastructure.database.initializer.seeder;

import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.phase.Phase;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.infrastructure.database.repositories.PhaseRepository;
import fr.siamois.infrastructure.database.repositories.actionunit.ActionUnitRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PhaseSeeder {

    private final PhaseRepository phaseRepository;
    private final ConceptSeeder conceptSeeder;
    private final ActionUnitRepository actionUnitRepository;
    private final PersonSeeder personSeeder;

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

    public void seed(List<PhaseSpecs> specs) {
        for (int i = 0; i < specs.size(); i++) {
            var s = specs.get(i);
            try {
                ActionUnit au = SeederUtils.field("projet",
                        () -> actionUnitRepository
                                .findByIdentifierAndCreatedByInstitutionIdentifier(
                                        s.actionUnitKey().fullIdentifier(),
                                        s.actionUnitKey().institutionIdentifier())
                                .orElseThrow(() -> new IllegalStateException("Projet introuvable")));

                Concept type = s.type() != null
                        ? SeederUtils.field("type", () -> conceptSeeder.findConceptOrThrow(s.type()))
                        : null;

                Person author = s.authorEmail() != null && !s.authorEmail().isBlank()
                        ? SeederUtils.field("auteur", () -> personSeeder.findOrCreatePerson(s.authorEmail()))
                        : null;

                Optional<Phase> existing = phaseRepository
                        .findByIdentifierAndActionUnitId(s.identifier(), au.getId());
                if (existing.isEmpty()) {
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
                    phaseRepository.save(phase);
                }
            } catch (Exception e) {
                throw new IllegalStateException(
                        "[Phase ligne " + (i + 1) + "] '" + s.identifier() + "' : " + e.getMessage(), e);
            }
        }
    }
}
