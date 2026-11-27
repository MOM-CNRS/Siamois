package fr.siamois.domain.services.relation;

import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.domain.models.relation.RelationDefinition;
import fr.siamois.domain.models.relation.RelationRule;
import fr.siamois.infrastructure.database.repositories.actionunit.ActionUnitRepository;
import fr.siamois.infrastructure.database.repositories.relation.RelationRuleExclusionRepository;
import fr.siamois.infrastructure.database.repositories.relation.RelationRuleKindRepository;
import fr.siamois.infrastructure.database.repositories.relation.RelationRuleMirrorConceptRepository;
import fr.siamois.infrastructure.database.repositories.relation.RelationRuleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Which rules a link of a definition obeys in a project (docs/modele-relations.md, §3.3): those of the project,
 * else of its institution, else the platform default. A rule set is complete: the levels are never merged.
 */
@Service
@RequiredArgsConstructor
public class RelationRuleResolver {

    private final RelationRuleRepository ruleRepository;
    private final RelationRuleKindRepository kindRepository;
    private final RelationRuleMirrorConceptRepository mirrorRepository;
    private final RelationRuleExclusionRepository exclusionRepository;
    private final ActionUnitRepository actionUnitRepository;

    @Transactional(readOnly = true)
    public RelationRuleSet rulesFor(RelationDefinition definition, long projectId) {
        return findStoredRule(definition, projectId).map(this::loadRule).orElseGet(() -> RelationRuleSet.none(definition));
    }

    /** The stored rule set a project reads, if the definition has one in some scope. */
    @Transactional(readOnly = true)
    public Optional<RelationRule> storedRuleFor(RelationDefinition definition, long projectId) {
        return findStoredRule(definition, projectId);
    }

    private Optional<RelationRule> findStoredRule(RelationDefinition definition, long projectId) {
        Optional<RelationRule> ofProject = ruleRepository.findOfProject(definition.getId(), projectId);
        if (ofProject.isPresent()) return ofProject;
        Long institutionId = actionUnitRepository.findById(projectId)
                .map(ActionUnit::getCreatedByInstitution)
                .map(fr.siamois.domain.models.institution.Institution::getId)
                .orElse(null);
        if (institutionId != null) {
            Optional<RelationRule> ofInstitution = ruleRepository.findOfInstitution(definition.getId(), institutionId);
            if (ofInstitution.isPresent()) return ofInstitution;
        }
        return ruleRepository.findPlatformDefault(definition.getId());
    }

    /** A stored rule set, with its accepted kinds, mirror pairs and exclusions. */
    @Transactional(readOnly = true)
    public RelationRuleSet load(RelationRule rule) {
        return loadRule(rule);
    }

    private RelationRuleSet loadRule(RelationRule rule) {
        var kinds = kindRepository.findByRuleId(rule.getId()).stream()
                .map(k -> new RelationRuleSet.AcceptedKind(k.getSide(), k.getKind(),
                        k.getSubtype() != null ? k.getSubtype().getId() : null))
                .toList();
        Map<Long, Long> mirrors = mirrorRepository.findByRuleId(rule.getId()).stream()
                .collect(Collectors.toMap(m -> m.getConcept().getId(), m -> m.getMirrorConcept().getId()));
        var exclusions = exclusionRepository.findByRuleId(rule.getId()).stream()
                .map(e -> e.getExcludedDefinition().getId())
                .toList();
        return new RelationRuleSet(rule.getDefinition(), rule, rule.isNoCycle(), rule.isSourceUnique(),
                rule.isTargetUnique(), kinds, mirrors, exclusions);
    }
}
