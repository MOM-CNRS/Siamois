package fr.siamois.domain.services.relation;

import fr.siamois.domain.models.form.config.ConceptState;
import fr.siamois.domain.models.form.config.VocabularyMode;
import fr.siamois.domain.models.form.customfield.vocabulary.CustomFieldConcept;
import fr.siamois.domain.models.relation.RelationDefinition;
import fr.siamois.domain.models.relation.RelationEndKind;
import fr.siamois.domain.models.relation.RelationLink;
import fr.siamois.domain.models.relation.RelationSide;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.models.vocabulary.FieldVocabularyConfig;
import fr.siamois.domain.services.relation.RelationLinkException.Reason;
import fr.siamois.domain.services.vocabulary.FieldConfigurationService;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.infrastructure.database.repositories.relation.RelationDefinitionRepository;
import fr.siamois.infrastructure.database.repositories.relation.RelationLinkQueries;
import fr.siamois.infrastructure.database.repositories.relation.RelationLinkRepository;
import fr.siamois.infrastructure.database.repositories.relation.RelationRuleExclusionRepository;
import fr.siamois.infrastructure.database.repositories.vocabulary.ConceptRepository;
import lombok.RequiredArgsConstructor;
import org.hibernate.Hibernate;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Writes the links of the configurable relations, with the checks of docs/modele-relations.md, §4: the records
 * exist and are within reach, the caller may edit them, both belong to the same project, the rules of that project
 * accept their kinds, and the link breaks no rule (duplicate, single choice, exclusion, cycle).
 * <p>
 * A link is entered from one record, through the field of one side: the source field ("Postérieur à") or the target
 * one, its mirror ("Antérieur à"). It is stored once, from its source to its target; a qualifier picked from the
 * target's side is stored as the concept it is the mirror of. A symmetric pair is stored in one order only.
 */
@Service
@RequiredArgsConstructor
public class RelationLinkService {

    private final RelationDefinitionRepository definitionRepository;
    private final RelationLinkRepository linkRepository;
    private final RelationLinkQueries linkQueries;
    private final RelationRuleExclusionRepository exclusionRepository;
    private final RelationRuleResolver ruleResolver;
    private final RelationEndpointResolver endpointResolver;
    private final RelationWriteAuthorizer authorizer;
    private final ConceptRepository conceptRepository;
    private final FieldConfigurationService fieldConfigurationService;

    /**
     * @param side      the side of the field the link is entered through
     * @param record    the record being edited, on that side
     * @param other     the record at the other end
     * @param qualifier for a complex relation, the qualifier as the edited record shows it, or null
     */
    public record CreateRequest(Long definitionId, RelationSide side, End record, End other,
                                @Nullable Long qualifier, boolean uncertain) {
    }

    /** @param side the side of the field the link is edited through */
    public record UpdateRequest(RelationSide side, @Nullable Long qualifier, boolean uncertain) {
    }

    /** A record, by kind and id. */
    public record End(RelationEndKind kind, Long id) {
    }

    /** The stored link and the warnings of the rules it breaks without being refused (a cycle through an uncertain link). */
    public record Result(RelationLink link, List<RuleViolation> warnings) {
    }

    @Transactional(rollbackFor = Exception.class)
    public Result create(CreateRequest request, PersonDTO caller, Set<Long> accessibleInstitutionIds) {
        RelationDefinition definition = definitionRepository.findById(request.definitionId())
                .orElseThrow(() -> new RelationLinkException(Reason.DEFINITION_NOT_FOUND,
                        "Relation inconnue : " + request.definitionId()));
        RelationEndpoint edited = endpoint(request.record(), accessibleInstitutionIds);
        RelationEndpoint other = endpoint(request.other(), accessibleInstitutionIds);
        boolean fromTarget = request.side() == RelationSide.TARGET && !definition.isSymmetric();
        RelationEndpoint source = fromTarget ? other : edited;
        RelationEndpoint target = fromTarget ? edited : other;

        if (source.sameRecord(target)) {
            throw new RelationLinkException(Reason.SELF_LINK, "Une fiche ne peut pas être reliée à elle-même");
        }
        long projectId = sameProject(source, target);
        requireCanEdit(edited, caller);
        if (fromTarget) requireCanEdit(source, caller);

        RelationRuleSet rules = ruleResolver.rulesFor(definition, projectId);
        requireAccepted(rules, source, target);
        Concept qualifier = qualifierOf(definition, rules, request.qualifier(), fromTarget, projectId);

        if (definition.isSymmetric() && source.isAfter(target)) {
            RelationEndpoint swapped = source;
            source = target;
            target = swapped;
        }
        if (rules.needsLock()) linkQueries.lockProject(projectId);
        if (definition.isSimple() && linkQueries.linked(definition.getId(), source.kind(), source.id(),
                target.kind(), target.id())) {
            throw duplicate();
        }
        List<RuleViolation> warnings = checkRules(rules, projectId, source, target, request.uncertain());

        RelationLink link = new RelationLink();
        link.setDefinition(definition);
        link.setProjectId(projectId);
        link.setSourceKind(source.kind());
        link.setSourceId(source.id());
        link.setTargetKind(target.kind());
        link.setTargetId(target.id());
        link.setQualifier(qualifier);
        link.setUncertain(request.uncertain());
        link.setSimple(definition.isSimple());
        try {
            return new Result(linkRepository.saveAndFlush(link), warnings);
        } catch (DataIntegrityViolationException e) {
            // The partial unique index caught a duplicate written meanwhile by another transaction.
            throw duplicate();
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public Result update(Long linkId, UpdateRequest request, PersonDTO caller, Set<Long> accessibleInstitutionIds) {
        RelationLink link = linkOf(linkId);
        RelationDefinition definition = link.getDefinition();
        RelationEndpoint source = endpoint(new End(link.getSourceKind(), link.getSourceId()), accessibleInstitutionIds);
        RelationEndpoint target = endpoint(new End(link.getTargetKind(), link.getTargetId()), accessibleInstitutionIds);
        boolean fromTarget = request.side() == RelationSide.TARGET && !definition.isSymmetric();
        requireCanEditLink(definition, request.side(), source, target, caller);

        RelationRuleSet rules = ruleResolver.rulesFor(definition, link.getProjectId());
        link.setQualifier(qualifierOf(definition, rules, request.qualifier(), fromTarget, link.getProjectId()));
        List<RuleViolation> warnings = List.of();
        if (link.isUncertain() && !request.uncertain() && rules.forbidsCycles() && !definition.isSymmetric()) {
            // A cycle this link only took part in as uncertain becomes a refusal once the link is certain.
            linkQueries.lockProject(link.getProjectId());
            warnings = cycleCheck(rules, link.getProjectId(), source, target, false);
        }
        link.setUncertain(request.uncertain());
        return new Result(linkRepository.save(link), warnings);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long linkId, RelationSide side, PersonDTO caller, Set<Long> accessibleInstitutionIds) {
        RelationLink link = linkOf(linkId);
        RelationEndpoint source = endpoint(new End(link.getSourceKind(), link.getSourceId()), accessibleInstitutionIds);
        RelationEndpoint target = endpoint(new End(link.getTargetKind(), link.getTargetId()), accessibleInstitutionIds);
        requireCanEditLink(link.getDefinition(), side, source, target, caller);
        linkRepository.delete(link);
    }

    // ---- checks ----

    private RelationEndpoint endpoint(End end, Set<Long> accessibleInstitutionIds) {
        RelationEndpoint endpoint = end == null ? null : endpointResolver.find(end.kind(), end.id()).orElse(null);
        if (endpoint == null || (endpoint.institutionId() != null && accessibleInstitutionIds != null
                && !accessibleInstitutionIds.contains(endpoint.institutionId()))) {
            throw new RelationLinkException(Reason.ENDPOINT_NOT_FOUND, "Fiche introuvable : "
                    + (end == null ? "" : end.kind() + " " + end.id()));
        }
        return endpoint;
    }

    /** Both records belong to one project, which the link keeps. Places, which belong to none, are not accepted yet. */
    private static long sameProject(RelationEndpoint source, RelationEndpoint target) {
        if (source.projectId() == null || target.projectId() == null) {
            throw new RelationLinkException(Reason.KIND_NOT_ALLOWED, "Seules les fiches d'un projet peuvent être reliées");
        }
        if (!source.projectId().equals(target.projectId())) {
            throw new RelationLinkException(Reason.CROSS_PROJECT, "Les deux fiches doivent appartenir au même projet");
        }
        return source.projectId();
    }

    /**
     * Editing an existing link: through the source field, the right to edit the source; through the mirror field,
     * the right to edit both records; for a symmetric relation, whose pair is stored in an order of its own, the
     * right to edit either record.
     */
    private void requireCanEditLink(RelationDefinition definition, RelationSide side, RelationEndpoint source,
                                    RelationEndpoint target, PersonDTO caller) {
        boolean allowed = definition.isSymmetric()
                ? authorizer.canEdit(source, caller) || authorizer.canEdit(target, caller)
                : authorizer.canEdit(source, caller) && (side != RelationSide.TARGET || authorizer.canEdit(target, caller));
        if (!allowed) {
            throw new RelationLinkException(Reason.FORBIDDEN, "Modification non autorisée");
        }
    }

    private void requireCanEdit(RelationEndpoint endpoint, PersonDTO caller) {
        if (!authorizer.canEdit(endpoint, caller)) {
            throw new RelationLinkException(Reason.FORBIDDEN, "Modification non autorisée");
        }
    }

    /** Each end is accepted by its side; a symmetric relation has one field, so either side's kinds will do. */
    private static void requireAccepted(RelationRuleSet rules, RelationEndpoint source, RelationEndpoint target) {
        boolean symmetric = rules.definition().isSymmetric();
        requireAccepted(rules, symmetric ? null : RelationSide.SOURCE, source);
        requireAccepted(rules, symmetric ? null : RelationSide.TARGET, target);
    }

    private static void requireAccepted(RelationRuleSet rules, @Nullable RelationSide side, RelationEndpoint end) {
        Long typeId = end.type() != null ? end.type().getId() : null;
        List<RelationSide> sides = side != null ? List.of(side) : List.of(RelationSide.values());
        if (sides.stream().anyMatch(s -> rules.accepts(s, end.kind(), typeId))) return;
        if (sides.stream().anyMatch(s -> rules.acceptsKind(s, end.kind()))) {
            throw new RelationLinkException(Reason.TYPE_NOT_ALLOWED,
                    "Le type de cette fiche n'est pas accepté de ce côté de la relation");
        }
        throw new RelationLinkException(Reason.KIND_NOT_ALLOWED,
                "Ce genre de fiche n'est pas accepté de ce côté de la relation");
    }

    /**
     * The qualifier to store: none for a simple relation; for a complex one, the source's view of the picked concept,
     * which the qualifier field must offer in the project (as for any answer of that field).
     */
    private Concept qualifierOf(RelationDefinition definition, RelationRuleSet rules, @Nullable Long picked,
                                boolean fromTarget, long projectId) {
        if (picked == null) return null;
        if (definition.isSimple()) {
            throw new RelationLinkException(Reason.INVALID_QUALIFIER, "Une relation simple n'a pas de qualificatif");
        }
        Long stored = fromTarget ? rules.qualifierForMirror(picked) : picked;
        Concept concept = conceptRepository.findById(stored).orElseThrow(() ->
                new RelationLinkException(Reason.INVALID_QUALIFIER, "Qualificatif inconnu : " + stored));
        if (Hibernate.unproxy(definition.getQualifierField()) instanceof CustomFieldConcept field
                && !offered(fieldConfigurationService.describeVocabulary(field, projectId, null), stored)) {
            throw new RelationLinkException(Reason.INVALID_QUALIFIER,
                    "Ce qualificatif n'est pas proposé pour cette relation dans ce projet");
        }
        return concept;
    }

    /** Whether a field's vocabulary in a project offers a concept: the rule the answers of the field follow. */
    private static boolean offered(@Nullable FieldVocabularyConfig vocabulary, Long conceptId) {
        if (vocabulary == null) return true;
        ConceptState state = vocabulary.states().get(conceptId);
        return vocabulary.mode() == VocabularyMode.FROZEN ? state == ConceptState.ENABLED : state != ConceptState.DISABLED;
    }

    /** Blocking violations refuse the write; the warnings come back with the stored link. */
    private List<RuleViolation> checkRules(RelationRuleSet rules, long projectId, RelationEndpoint source,
                                           RelationEndpoint target, boolean uncertain) {
        RelationDefinition definition = rules.definition();
        boolean symmetric = definition.isSymmetric();
        if (rules.sourceUnique() || (symmetric && rules.targetUnique())) {
            requireSingle(definition, RelationSide.SOURCE, symmetric, source);
            if (symmetric) requireSingle(definition, RelationSide.TARGET, true, target);
        }
        if (rules.targetUnique() && !symmetric) {
            requireSingle(definition, RelationSide.TARGET, false, target);
        }
        Set<Long> excluded = excludedDefinitions(rules, projectId);
        if (linkQueries.linkedEitherWay(excluded, source.kind(), source.id(), target.kind(), target.id())) {
            throw refused(new RuleViolation(RuleViolation.Rule.EXCLUSION, true,
                    "Ces deux fiches sont déjà reliées par une relation exclusive de celle-ci", List.of()));
        }
        return rules.forbidsCycles() && !symmetric
                ? cycleCheck(rules, projectId, source, target, uncertain)
                : List.of();
    }

    private void requireSingle(RelationDefinition definition, RelationSide side, boolean bothSides,
                               RelationEndpoint end) {
        if (linkQueries.countAt(definition.getId(), side, bothSides, end.kind(), end.id()) > 0) {
            throw refused(new RuleViolation(RuleViolation.Rule.SINGLE_CHOICE, true,
                    "Cette fiche a déjà un lien pour cette relation, qui n'en accepte qu'un", List.of()));
        }
    }

    /**
     * The definitions this one cannot share a pair with in the project: those its rules exclude, and those whose
     * own rules in the project exclude it, so that an exclusion declared on one side only still holds.
     */
    private Set<Long> excludedDefinitions(RelationRuleSet rules, long projectId) {
        Set<Long> excluded = new HashSet<>(rules.excludedDefinitionIds());
        Long definitionId = rules.definition().getId();
        for (Long otherId : exclusionRepository.findDefinitionsExcluding(definitionId)) {
            if (excluded.contains(otherId) || otherId.equals(definitionId)) continue;
            definitionRepository.findById(otherId)
                    .map(other -> ruleResolver.rulesFor(other, projectId))
                    .filter(otherRules -> otherRules.excludedDefinitionIds().contains(definitionId))
                    .ifPresent(otherRules -> excluded.add(otherId));
        }
        return excluded;
    }

    /**
     * Adding source → target closes a cycle when the target already leads to the source. Refused when the new link
     * and a path back are all certain; a warning when only uncertain links close it.
     */
    private List<RuleViolation> cycleCheck(RelationRuleSet rules, long projectId, RelationEndpoint source,
                                           RelationEndpoint target, boolean uncertain) {
        long definitionId = rules.definition().getId();
        if (!uncertain && linkQueries.reaches(definitionId, target.kind(), target.id(), source.kind(), source.id(), true)) {
            throw refused(new RuleViolation(RuleViolation.Rule.NO_CYCLE, true, "Ce lien créerait une boucle",
                    pathBetween(definitionId, projectId, target, source, true)));
        }
        if (linkQueries.reaches(definitionId, target.kind(), target.id(), source.kind(), source.id(), false)) {
            return List.of(new RuleViolation(RuleViolation.Rule.NO_CYCLE, false,
                    "Ce lien crée une boucle, qui passe par un lien incertain",
                    pathBetween(definitionId, projectId, target, source, false)));
        }
        return List.of();
    }

    /** One shortest path of links from one record to another, for the message of a cycle (breadth first). */
    private List<Long> pathBetween(long definitionId, long projectId, RelationEndpoint from, RelationEndpoint to,
                                   boolean certainOnly) {
        Map<String, List<RelationLink>> outgoing = new HashMap<>();
        for (RelationLink link : linkRepository.findOfProjects(definitionId, List.of(projectId))) {
            if (certainOnly && link.isUncertain()) continue;
            outgoing.computeIfAbsent(key(link.getSourceKind(), link.getSourceId()), k -> new ArrayList<>()).add(link);
        }
        String goal = key(to.kind(), to.id());
        Map<String, RelationLink> reachedBy = new HashMap<>();
        Set<String> visited = new LinkedHashSet<>();
        Deque<String> queue = new ArrayDeque<>();
        String start = key(from.kind(), from.id());
        queue.add(start);
        visited.add(start);
        while (!queue.isEmpty()) {
            String current = queue.poll();
            if (current.equals(goal)) break;
            for (RelationLink link : outgoing.getOrDefault(current, List.of())) {
                String next = key(link.getTargetKind(), link.getTargetId());
                if (visited.add(next)) {
                    reachedBy.put(next, link);
                    queue.add(next);
                }
            }
        }
        List<Long> path = new ArrayList<>();
        for (String node = goal; reachedBy.containsKey(node); ) {
            RelationLink link = reachedBy.get(node);
            path.add(0, link.getId());
            node = key(link.getSourceKind(), link.getSourceId());
        }
        return path;
    }

    private static String key(RelationEndKind kind, Long id) {
        return kind.name() + ':' + id;
    }

    private RelationLink linkOf(Long linkId) {
        return linkRepository.findById(linkId).orElseThrow(() ->
                new RelationLinkException(Reason.LINK_NOT_FOUND, "Lien introuvable : " + linkId));
    }

    private static RelationLinkException duplicate() {
        return new RelationLinkException(Reason.DUPLICATE, "Ces deux fiches sont déjà reliées par cette relation");
    }

    private static RelationLinkException refused(RuleViolation violation) {
        return new RelationLinkException(Reason.RULE_VIOLATION, violation.message(), List.of(violation));
    }
}
