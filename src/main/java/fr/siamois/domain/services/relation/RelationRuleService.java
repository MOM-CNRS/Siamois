package fr.siamois.domain.services.relation;

import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.domain.models.institution.Institution;
import fr.siamois.domain.models.relation.RelationDefinition;
import fr.siamois.domain.models.relation.RelationEndKind;
import fr.siamois.domain.models.relation.RelationLink;
import fr.siamois.domain.models.relation.RelationRule;
import fr.siamois.domain.models.relation.RelationRuleExclusion;
import fr.siamois.domain.models.relation.RelationRuleKind;
import fr.siamois.domain.models.relation.RelationRuleMirrorConcept;
import fr.siamois.domain.models.relation.RelationSide;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.services.relation.RelationLinkException.Reason;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.infrastructure.database.repositories.actionunit.ActionUnitRepository;
import fr.siamois.infrastructure.database.repositories.institution.InstitutionRepository;
import fr.siamois.infrastructure.database.repositories.relation.RelationDefinitionRepository;
import fr.siamois.infrastructure.database.repositories.relation.RelationLinkQueries;
import fr.siamois.infrastructure.database.repositories.relation.RelationLinkRepository;
import fr.siamois.infrastructure.database.repositories.relation.RelationRuleExclusionRepository;
import fr.siamois.infrastructure.database.repositories.relation.RelationRuleKindRepository;
import fr.siamois.infrastructure.database.repositories.relation.RelationRuleMirrorConceptRepository;
import fr.siamois.infrastructure.database.repositories.relation.RelationRuleRepository;
import fr.siamois.infrastructure.database.repositories.vocabulary.ConceptRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * The rule sets of the relations, per scope (docs/modele-relations.md, §3.3 and §4.4). Saving the rules of a scope
 * that has none of its own creates them; a rule set that the existing links of the projects reading it would break
 * is refused, with the links in cause. Widening a rule is always possible.
 */
@Service
@RequiredArgsConstructor
public class RelationRuleService {

    private final RelationDefinitionRepository definitionRepository;
    private final RelationRuleRepository ruleRepository;
    private final RelationRuleKindRepository kindRepository;
    private final RelationRuleMirrorConceptRepository mirrorRepository;
    private final RelationRuleExclusionRepository exclusionRepository;
    private final RelationLinkRepository linkRepository;
    private final RelationLinkQueries linkQueries;
    private final RelationRuleResolver ruleResolver;
    private final RelationEndpointResolver endpointResolver;
    private final RelationConfigAuthorizer configAuthorizer;
    private final ConceptRepository conceptRepository;
    private final InstitutionRepository institutionRepository;
    private final ActionUnitRepository actionUnitRepository;

    /**
     * The content of a rule set.
     *
     * @param kinds                 the kinds each side accepts; none on a side: any record
     * @param mirrorByConcept       qualifier concept id → its mirror's id, one to one
     * @param excludedDefinitionIds the definitions that cannot link the same pair
     */
    public record RuleSpec(boolean noCycle, boolean sourceUnique, boolean targetUnique,
                           List<RelationRuleSet.AcceptedKind> kinds, Map<Long, Long> mirrorByConcept,
                           List<Long> excludedDefinitionIds) {

        public RuleSpec {
            kinds = List.copyOf(kinds);
            mirrorByConcept = Map.copyOf(mirrorByConcept);
            excludedDefinitionIds = List.copyOf(excludedDefinitionIds);
        }

        public static RuleSpec of(RelationRuleSet rules) {
            return new RuleSpec(rules.noCycle(), rules.sourceUnique(), rules.targetUnique(), rules.kinds(),
                    rules.mirrorByConcept(), rules.excludedDefinitionIds());
        }
    }

    /** The rules a project's links obey, whichever scope they come from. */
    @Transactional(readOnly = true)
    public RelationRuleSet rulesFor(Long definitionId, long projectId) {
        return ruleResolver.rulesFor(definition(definitionId), projectId);
    }

    /** The scope's own rule set, if it has one. */
    @Transactional(readOnly = true)
    public Optional<RelationRuleSet> ownRules(Long definitionId, RelationScope scope) {
        return storedRule(definitionId, scope).map(ruleResolver::load);
    }

    @Transactional(rollbackFor = Exception.class)
    public RelationRuleSet save(Long definitionId, RelationScope scope, RuleSpec spec, PersonDTO caller) {
        requireCanEdit(scope, caller);
        return store(definition(definitionId), scope, spec);
    }

    /**
     * Removes the scope's own rule set: its projects then read the next level. Refused when the next level's rules
     * are broken by their links. The platform default can always go: with none, nothing is restricted.
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long definitionId, RelationScope scope, PersonDTO caller) {
        requireCanEdit(scope, caller);
        RelationDefinition definition = definition(definitionId);
        Optional<RelationRule> own = storedRule(definitionId, scope);
        if (own.isEmpty()) return;
        if (!scope.isPlatform()) {
            List<Long> projects = linkQueries.projectsReading(definitionId, scope.institutionId(), scope.projectId());
            RelationScope parent = scope.projectId() != null
                    ? RelationScope.institution(scope.institutionId()) : RelationScope.platform();
            RelationRuleSet fallback = nearestRules(definition, parent);
            requireNoViolation(checkAgainstLinks(definition, RuleSpec.of(fallback), projects));
        }
        deleteRule(own.get());
    }

    /** Copies the rule sets of a project onto a new one, as the project templates copy its form configuration. */
    @Transactional(rollbackFor = Exception.class)
    public void copyProjectRules(long sourceProjectId, ActionUnit target) {
        for (RelationRule rule : ruleRepository.findAllOfProject(sourceProjectId)) {
            RelationRule copy = new RelationRule();
            copy.setDefinition(rule.getDefinition());
            copy.setInstitution(target.getCreatedByInstitution());
            copy.setActionUnit(target);
            writeContent(ruleRepository.save(copy), RuleSpec.of(ruleResolver.load(rule)));
        }
    }

    /** Removes every rule set of a definition, before the definition itself goes. */
    @Transactional(rollbackFor = Exception.class)
    public void deleteAllOf(RelationDefinition definition) {
        ruleRepository.findByDefinitionId(definition.getId()).forEach(this::deleteRule);
    }

    /** Saves a rule set with no permission check: for the definition service, which checked its own. */
    @Transactional(rollbackFor = Exception.class)
    RelationRuleSet saveWithoutPermission(RelationDefinition definition, RelationScope scope, RuleSpec spec) {
        return store(definition, scope, spec);
    }

    private RelationRuleSet store(RelationDefinition definition, RelationScope scope, RuleSpec spec) {
        RuleSpec normalized = normalize(definition, spec);
        List<Long> projects = linkQueries.projectsReading(definition.getId(), scope.institutionId(), scope.projectId());
        requireNoViolation(checkAgainstLinks(definition, normalized, projects));

        RelationRule rule = storedRule(definition.getId(), scope).orElseGet(() -> newRule(definition, scope));
        rule.setNoCycle(normalized.noCycle());
        rule.setSourceUnique(normalized.sourceUnique());
        rule.setTargetUnique(normalized.targetUnique());
        RelationRule saved = ruleRepository.save(rule);
        kindRepository.deleteByRuleId(saved.getId());
        mirrorRepository.deleteByRuleId(saved.getId());
        exclusionRepository.deleteByRuleId(saved.getId());
        writeContent(saved, normalized);
        return ruleResolver.load(saved);
    }

    // ---- what existing links break ----

    /** The rules the links of the projects break, each with its links. */
    List<RuleViolation> checkAgainstLinks(RelationDefinition definition, RuleSpec spec, List<Long> projectIds) {
        if (projectIds.isEmpty()) return List.of();
        RelationRuleSet rules = new RelationRuleSet(definition, null, spec.noCycle(), spec.sourceUnique(),
                spec.targetUnique(), spec.kinds(), spec.mirrorByConcept(), spec.excludedDefinitionIds());
        List<RelationLink> links = linkRepository.findOfProjects(definition.getId(), projectIds);
        List<RuleViolation> violations = new ArrayList<>();

        List<Long> notAccepted = linksNotAccepted(rules, links);
        if (!notAccepted.isEmpty()) {
            violations.add(new RuleViolation(RuleViolation.Rule.ACCEPTED_KIND, true,
                    "Des liens existants relient des fiches que ces règles n'acceptent plus", notAccepted));
        }
        List<Long> notSingle = linksBreakingSingleChoice(rules, links);
        if (!notSingle.isEmpty()) {
            violations.add(new RuleViolation(RuleViolation.Rule.SINGLE_CHOICE, true,
                    "Des fiches ont déjà plusieurs liens pour cette relation", notSingle));
        }
        if (rules.forbidsCycles() && !definition.isSymmetric()) {
            List<Long> inCycles = linkQueries.linksInCycles(definition.getId(), projectIds);
            if (!inCycles.isEmpty()) {
                violations.add(new RuleViolation(RuleViolation.Rule.NO_CYCLE, true,
                        "Des liens existants forment des boucles", inCycles));
            }
        }
        for (Long excluded : spec.excludedDefinitionIds()) {
            List<Long> shared = linkQueries.linksSharingAPairWith(definition.getId(), excluded, projectIds);
            if (!shared.isEmpty()) {
                violations.add(new RuleViolation(RuleViolation.Rule.EXCLUSION, true,
                        "Des fiches sont déjà reliées par les deux relations", shared));
            }
        }
        return violations;
    }

    private List<Long> linksNotAccepted(RelationRuleSet rules, List<RelationLink> links) {
        if (rules.kinds().isEmpty()) return List.of();
        Map<RelationEndKind, Set<Long>> idsByKind = new EnumMap<>(RelationEndKind.class);
        for (RelationLink link : links) {
            idsByKind.computeIfAbsent(link.getSourceKind(), k -> new HashSet<>()).add(link.getSourceId());
            idsByKind.computeIfAbsent(link.getTargetKind(), k -> new HashSet<>()).add(link.getTargetId());
        }
        Map<RelationEndKind, Map<Long, RelationEndpoint>> records = new EnumMap<>(RelationEndKind.class);
        idsByKind.forEach((kind, ids) -> records.put(kind, endpointResolver.findAll(kind, ids)));
        boolean symmetric = rules.definition().isSymmetric();
        List<Long> notAccepted = new ArrayList<>();
        for (RelationLink link : links) {
            boolean ok = accepted(rules, symmetric ? null : RelationSide.SOURCE,
                    records.get(link.getSourceKind()).get(link.getSourceId()), link.getSourceKind())
                    && accepted(rules, symmetric ? null : RelationSide.TARGET,
                    records.get(link.getTargetKind()).get(link.getTargetId()), link.getTargetKind());
            if (!ok) notAccepted.add(link.getId());
        }
        return notAccepted;
    }

    private static boolean accepted(RelationRuleSet rules, RelationSide side, RelationEndpoint end,
                                    RelationEndKind kind) {
        Long typeId = end != null && end.type() != null ? end.type().getId() : null;
        return side != null
                ? rules.accepts(side, kind, typeId)
                : rules.accepts(RelationSide.SOURCE, kind, typeId) || rules.accepts(RelationSide.TARGET, kind, typeId);
    }

    private static List<Long> linksBreakingSingleChoice(RelationRuleSet rules, List<RelationLink> links) {
        boolean symmetric = rules.definition().isSymmetric();
        boolean bySource = rules.sourceUnique() || (symmetric && rules.targetUnique());
        boolean byTarget = rules.targetUnique() || (symmetric && rules.sourceUnique());
        if (!bySource && !byTarget) return List.of();
        Set<Long> breaking = new TreeSet<>();
        for (Map<String, List<Long>> ends : linksByEnd(links, symmetric, bySource, byTarget)) {
            ends.values().stream().filter(ids -> ids.size() > 1).forEach(breaking::addAll);
        }
        return List.copyOf(breaking);
    }

    /**
     * The links grouped by the record at their unique end(s): by source and by target, or both ends together for a
     * symmetric relation.
     */
    private static List<Map<String, List<Long>>> linksByEnd(List<RelationLink> links, boolean symmetric,
                                                            boolean bySource, boolean byTarget) {
        Map<String, List<Long>> bySourceEnd = new HashMap<>();
        Map<String, List<Long>> byTargetEnd = new HashMap<>();
        for (RelationLink link : links) {
            String source = link.getSourceKind() + ":" + link.getSourceId();
            String target = link.getTargetKind() + ":" + link.getTargetId();
            if (symmetric) {
                addTo(bySourceEnd, source, link.getId());
                addTo(bySourceEnd, target, link.getId());
                continue;
            }
            if (bySource) addTo(bySourceEnd, source, link.getId());
            if (byTarget) addTo(byTargetEnd, target, link.getId());
        }
        return List.of(bySourceEnd, byTargetEnd);
    }

    private static void addTo(Map<String, List<Long>> ends, String end, Long linkId) {
        ends.computeIfAbsent(end, k -> new ArrayList<>()).add(linkId);
    }

    // ---- internals ----

    /** A hierarchical relation never allows cycles; mirror pairs are one to one; exclusions name other definitions. */
    private RuleSpec normalize(RelationDefinition definition, RuleSpec spec) {
        if (new HashSet<>(spec.mirrorByConcept().values()).size() != spec.mirrorByConcept().size()) {
            throw invalid("Un concept ne peut être le miroir que d'un seul qualificatif");
        }
        if (!spec.mirrorByConcept().isEmpty() && definition.isSimple()) {
            throw invalid("Une relation simple n'a pas de qualificatif, donc pas de miroir");
        }
        for (Long excluded : spec.excludedDefinitionIds()) {
            if (excluded.equals(definition.getId()) || !definitionRepository.existsById(excluded)) {
                throw invalid("Exclusion invalide : " + excluded);
            }
        }
        return new RuleSpec(spec.noCycle() || definition.isHierarchical(), spec.sourceUnique(), spec.targetUnique(),
                spec.kinds(), spec.mirrorByConcept(), List.copyOf(new TreeSet<>(spec.excludedDefinitionIds())));
    }

    private void writeContent(RelationRule rule, RuleSpec spec) {
        for (RelationRuleSet.AcceptedKind accepted : spec.kinds()) {
            RelationRuleKind kind = new RelationRuleKind();
            kind.setRule(rule);
            kind.setSide(accepted.side());
            kind.setKind(accepted.kind());
            kind.setSubtype(accepted.subtypeId() != null ? concept(accepted.subtypeId()) : null);
            kindRepository.save(kind);
        }
        spec.mirrorByConcept().forEach((conceptId, mirrorId) -> {
            RelationRuleMirrorConcept mirror = new RelationRuleMirrorConcept();
            mirror.setRule(rule);
            mirror.setConcept(concept(conceptId));
            mirror.setMirrorConcept(concept(mirrorId));
            mirrorRepository.save(mirror);
        });
        for (Long excludedId : spec.excludedDefinitionIds()) {
            RelationRuleExclusion exclusion = new RelationRuleExclusion();
            exclusion.setRule(rule);
            exclusion.setExcludedDefinition(definition(excludedId));
            exclusionRepository.save(exclusion);
        }
    }

    private RelationRule newRule(RelationDefinition definition, RelationScope scope) {
        RelationRule rule = new RelationRule();
        rule.setDefinition(definition);
        Long institutionId = scope.institutionId();
        Long projectId = scope.projectId();
        if (institutionId != null) {
            Institution institution = institutionRepository.findById(institutionId)
                    .orElseThrow(() -> invalid("Institution inconnue : " + institutionId));
            rule.setInstitution(institution);
        }
        if (projectId != null) {
            ActionUnit project = actionUnitRepository.findById(projectId)
                    .orElseThrow(() -> invalid("Projet inconnu : " + projectId));
            if (project.getCreatedByInstitution() == null
                    || !project.getCreatedByInstitution().getId().equals(institutionId)) {
                throw invalid("Ce projet n'appartient pas à cette institution");
            }
            rule.setActionUnit(project);
        }
        return rule;
    }

    /** The rules a scope reads: its own, else its parent's, up to the platform default, else none. */
    private RelationRuleSet nearestRules(RelationDefinition definition, RelationScope scope) {
        Optional<RelationRule> own = storedRule(definition.getId(), scope);
        if (own.isPresent()) return ruleResolver.load(own.get());
        if (scope.projectId() != null) return nearestRules(definition, RelationScope.institution(scope.institutionId()));
        if (!scope.isPlatform()) return nearestRules(definition, RelationScope.platform());
        return RelationRuleSet.none(definition);
    }

    private Optional<RelationRule> storedRule(Long definitionId, RelationScope scope) {
        if (scope.projectId() != null) return ruleRepository.findOfProject(definitionId, scope.projectId());
        if (scope.institutionId() != null) return ruleRepository.findOfInstitution(definitionId, scope.institutionId());
        return ruleRepository.findPlatformDefault(definitionId);
    }

    private void deleteRule(RelationRule rule) {
        kindRepository.deleteByRuleId(rule.getId());
        mirrorRepository.deleteByRuleId(rule.getId());
        exclusionRepository.deleteByRuleId(rule.getId());
        ruleRepository.delete(rule);
    }

    private void requireCanEdit(RelationScope scope, PersonDTO caller) {
        if (!configAuthorizer.canEditRules(scope, caller)) {
            throw new RelationLinkException(Reason.FORBIDDEN, "Modification non autorisée");
        }
    }

    private static void requireNoViolation(List<RuleViolation> violations) {
        if (!violations.isEmpty()) {
            throw new RelationLinkException(Reason.INVALID_CONFIGURATION, violations.get(0).message(), violations);
        }
    }

    private RelationDefinition definition(Long id) {
        return definitionRepository.findById(id).orElseThrow(() ->
                new RelationLinkException(Reason.DEFINITION_NOT_FOUND, "Relation inconnue : " + id));
    }

    private Concept concept(Long id) {
        return conceptRepository.findById(id).orElseThrow(() -> invalid("Concept inconnu : " + id));
    }

    private static RelationLinkException invalid(String message) {
        return new RelationLinkException(Reason.INVALID_CONFIGURATION, message);
    }
}
