package fr.siamois.domain.services.relation;

import fr.siamois.domain.models.relation.RelationDefinition;
import fr.siamois.domain.models.relation.RelationDefinitionKind;
import fr.siamois.domain.models.relation.RelationEntityKind;
import fr.siamois.domain.models.relation.RelationLink;
import fr.siamois.domain.models.relation.RelationMappingMode;
import fr.siamois.domain.models.relation.RelationMirror;
import fr.siamois.domain.models.relation.RelationRule;
import fr.siamois.domain.models.relation.RelationRuleSeverity;
import fr.siamois.domain.models.relation.RelationSide;
import fr.siamois.domain.models.relation.RelationTypeMapping;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.services.relation.RelationLinkException.Reason;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.infrastructure.database.repositories.relation.RelationDefinitionRepository;
import fr.siamois.infrastructure.database.repositories.relation.RelationLinkQueries;
import fr.siamois.infrastructure.database.repositories.relation.RelationLinkRepository;
import fr.siamois.infrastructure.database.repositories.relation.RelationRuleRepository;
import fr.siamois.infrastructure.database.repositories.relation.RelationTypeMappingRepository;
import fr.siamois.infrastructure.database.repositories.vocabulary.ConceptRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Writes the links of the configurable relations: what a definition accepts at each end (kind of entity and
 * config type), that both ends belong to the same project, the correspondence of types, the definition's rules
 * (no cycle, cardinality, exclusion) and the caller's right to edit the entity of the side the link is entered from.
 * <p>
 * A link is stored once, oriented from its source end to its target end. {@code editedFrom} says which end the caller
 * is editing: the target end is the definition's mirror, read-only unless the definition lets it be edited. A
 * symmetric ({@link RelationMirror#SELF}) relation has one field, editable whichever end it is entered from, and
 * stores the lower id as its source.
 */
@Service
@RequiredArgsConstructor
public class RelationLinkService {

    private final RelationDefinitionRepository definitionRepository;
    private final RelationLinkRepository linkRepository;
    private final RelationLinkQueries linkQueries;
    private final RelationRuleRepository ruleRepository;
    private final RelationTypeMappingRepository mappingRepository;
    private final RelationEndpointResolver endpointResolver;
    private final RelationWriteAuthorizer authorizer;
    private final RelationLinkAttributesService attributesService;
    private final ConceptRepository conceptRepository;

    /**
     * @param editedFrom the end the caller enters the link from; {@code null} is the source end
     * @param attributes the link's own attribute values, by field id
     */
    public record CreateRequest(String definitionCode,
                                RelationEntityKind sourceKind, Long sourceId,
                                RelationEntityKind targetKind, Long targetId,
                                Long sourceTypeId, Long targetTypeId,
                                RelationSide editedFrom,
                                Map<Long, Object> attributes) {
    }

    /**
     * @param changeTypes whether {@code sourceTypeId} / {@code targetTypeId} replace the link's types (a {@code null}
     *                    id then clears the type)
     */
    public record UpdateRequest(boolean changeTypes, Long sourceTypeId, Long targetTypeId,
                                RelationSide editedFrom, Map<Long, Object> attributes) {
    }

    /** The stored link and the warnings of the non-blocking rules it breaks. */
    public record Result(RelationLink link, List<RuleViolation> warnings) {
    }

    @Transactional(rollbackFor = Exception.class)
    public Result create(CreateRequest request, PersonDTO caller, Set<Long> accessibleInstitutionIds) {
        RelationDefinition definition = definitionRepository.findByCode(request.definitionCode())
                .orElseThrow(() -> new RelationLinkException(Reason.DEFINITION_NOT_FOUND,
                        "Relation inconnue : " + request.definitionCode()));
        RelationEndpoint source = endpoint(request.sourceKind(), request.sourceId(), accessibleInstitutionIds);
        RelationEndpoint target = endpoint(request.targetKind(), request.targetId(), accessibleInstitutionIds);
        RelationSide editedFrom = request.editedFrom() != null ? request.editedFrom() : RelationSide.SOURCE;

        requireEditable(definition, editedFrom);
        requireWritePermission(editedFrom == RelationSide.SOURCE ? source : target, caller);
        requireAccepted(definition, RelationSide.SOURCE, source);
        requireAccepted(definition, RelationSide.TARGET, target);
        requireSameProject(source, target);
        if (source.kind() == target.kind() && Objects.equals(source.id(), target.id())) {
            throw new RelationLinkException(Reason.RULE_VIOLATION, "Un lien ne peut pas relier une entité à elle-même");
        }

        Concept sourceType = typeOf(request.sourceTypeId());
        Concept targetType = typeOf(request.targetTypeId());
        Concept[] types = applyMapping(definition, sourceType, targetType);
        sourceType = types[0];
        targetType = types[1];

        if (definition.getMirror() == RelationMirror.SELF) {
            if (source.kind() != target.kind()) {
                throw new RelationLinkException(Reason.KIND_NOT_ALLOWED,
                        "Une relation symétrique relie deux entités du même genre");
            }
            if (source.id() > target.id()) {
                RelationEndpoint swapped = source;
                source = target;
                target = swapped;
                Concept swappedType = sourceType;
                sourceType = targetType;
                targetType = swappedType;
            }
        }

        requireNotYetLinked(definition, source, target);
        List<RuleViolation> warnings = checkRules(definition, source, target);

        RelationLink link = new RelationLink();
        link.setDefinition(definition);
        link.attachSource(source.entity());
        link.attachTarget(target.entity());
        link.setSourceType(sourceType);
        link.setTargetType(targetType);
        RelationLink saved = linkRepository.save(link);
        attributesService.save(saved, request.attributes(), caller);
        return new Result(saved, warnings);
    }

    @Transactional(rollbackFor = Exception.class)
    public Result update(Long linkId, UpdateRequest request, PersonDTO caller, Set<Long> accessibleInstitutionIds) {
        RelationLink link = linkOf(linkId);
        RelationDefinition definition = link.getDefinition();
        RelationEndpoint source = endpointOf(link, RelationSide.SOURCE, accessibleInstitutionIds);
        RelationEndpoint target = endpointOf(link, RelationSide.TARGET, accessibleInstitutionIds);
        RelationSide editedFrom = request.editedFrom() != null ? request.editedFrom() : RelationSide.SOURCE;

        requireEditable(definition, editedFrom);
        requireWritePermission(editedFrom == RelationSide.SOURCE ? source : target, caller);

        if (request.changeTypes()) {
            Concept[] types = applyMapping(definition, typeOf(request.sourceTypeId()), typeOf(request.targetTypeId()));
            link.setSourceType(types[0]);
            link.setTargetType(types[1]);
            linkRepository.save(link);
        }
        attributesService.save(link, request.attributes(), caller);
        return new Result(link, List.of());
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long linkId, RelationSide editedFrom, PersonDTO caller, Set<Long> accessibleInstitutionIds) {
        RelationLink link = linkOf(linkId);
        RelationSide side = editedFrom != null ? editedFrom : RelationSide.SOURCE;
        requireEditable(link.getDefinition(), side);
        requireWritePermission(endpointOf(link, side, accessibleInstitutionIds), caller);
        attributesService.deleteAll(link);
        linkRepository.delete(link);
    }

    /**
     * Deletes every link of an entity, with its attributes, before the entity itself is deleted: the answers of a link
     * hang on it, so the database cannot cascade the deletion alone. No permission check: the caller is deleting the
     * entity, which is the stronger right.
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteAllLinksOf(RelationEntityKind kind, Long id) {
        for (RelationLink link : linkQueries.linksOfEntity(kind, id)) {
            attributesService.deleteAll(link);
            linkRepository.delete(link);
        }
    }

    /**
     * What an existing link no longer satisfies — a kind or a config type its definition stopped accepting, an end
     * in another project. A link that breaks a rule or a restriction added after the fact is flagged here, never
     * deleted.
     */
    @Transactional(readOnly = true)
    public List<String> audit(RelationLink link) {
        List<String> problems = new ArrayList<>();
        for (RelationSide side : RelationSide.values()) {
            RelationEndpoint endpoint = endpointOf(link, side, null);
            if (acceptance(link.getDefinition(), side, endpoint) != null) {
                problems.add(side + " : " + acceptance(link.getDefinition(), side, endpoint).getMessage());
            }
        }
        RelationEndpoint source = endpointOf(link, RelationSide.SOURCE, null);
        RelationEndpoint target = endpointOf(link, RelationSide.TARGET, null);
        if (differentProjects(source, target)) problems.add("Les deux extrémités sont dans des projets différents");
        return problems;
    }

    // ---- checks ----

    private RelationEndpoint endpoint(RelationEntityKind kind, Long id, Set<Long> accessibleInstitutionIds) {
        RelationEndpoint endpoint = endpointResolver.find(kind, id).orElse(null);
        if (endpoint == null || !reachable(endpoint, accessibleInstitutionIds)) {
            throw new RelationLinkException(Reason.ENDPOINT_NOT_FOUND, "Entité introuvable : " + kind + " " + id);
        }
        return endpoint;
    }

    private RelationEndpoint endpointOf(RelationLink link, RelationSide side, Set<Long> accessibleInstitutionIds) {
        RelationEntityKind kind = (side == RelationSide.SOURCE ? link.sourceKind() : link.targetKind()).orElseThrow();
        Long id = (side == RelationSide.SOURCE ? link.sourceId() : link.targetId()).orElseThrow();
        return endpoint(kind, id, accessibleInstitutionIds);
    }

    /** {@code null} accessible set: no caller to check against (the audit). A person has no institution: always reachable. */
    private static boolean reachable(RelationEndpoint endpoint, Set<Long> accessibleInstitutionIds) {
        return accessibleInstitutionIds == null || endpoint.institutionId() == null
                || accessibleInstitutionIds.contains(endpoint.institutionId());
    }

    private RelationLink linkOf(Long linkId) {
        return linkRepository.findById(linkId).orElseThrow(() ->
                new RelationLinkException(Reason.LINK_NOT_FOUND, "Lien introuvable : " + linkId));
    }

    private static void requireEditable(RelationDefinition definition, RelationSide editedFrom) {
        if (editedFrom == RelationSide.TARGET && definition.getMirror() != RelationMirror.SELF
                && !(definition.getMirror() == RelationMirror.OTHER && definition.isMirrorEditable())) {
            throw new RelationLinkException(Reason.MIRROR_READ_ONLY, "Ce champ est le miroir d'une relation : il est en lecture seule");
        }
    }

    private void requireWritePermission(RelationEndpoint endpoint, PersonDTO caller) {
        if (!authorizer.canEdit(endpoint, caller)) {
            throw new RelationLinkException(Reason.FORBIDDEN, "Modification non autorisée");
        }
    }

    private void requireAccepted(RelationDefinition definition, RelationSide side, RelationEndpoint endpoint) {
        RelationLinkException refusal = acceptance(definition, side, endpoint);
        if (refusal != null) throw refusal;
    }

    /** {@code null} when the definition accepts the entity on that side. */
    private static RelationLinkException acceptance(RelationDefinition definition, RelationSide side, RelationEndpoint endpoint) {
        boolean kindAccepted = false;
        for (RelationDefinitionKind accepted : definition.kindsOf(side)) {
            if (accepted.getEntityKind() != endpoint.kind()) continue;
            kindAccepted = true;
            if (accepted.getTypeConcept() == null) return null;
            if (endpoint.type() != null && Objects.equals(accepted.getTypeConcept().getId(), endpoint.type().getId())) return null;
        }
        return kindAccepted
                ? new RelationLinkException(Reason.TYPE_NOT_ALLOWED, "Le type de cette entité n'est pas accepté de ce côté de la relation")
                : new RelationLinkException(Reason.KIND_NOT_ALLOWED, "Ce genre d'entité n'est pas accepté de ce côté de la relation");
    }

    private void requireSameProject(RelationEndpoint source, RelationEndpoint target) {
        if (differentProjects(source, target)) {
            throw new RelationLinkException(Reason.CROSS_PROJECT, "Les deux entités doivent appartenir au même projet");
        }
    }

    private static boolean differentProjects(RelationEndpoint source, RelationEndpoint target) {
        return source.projectId() != null && target.projectId() != null
                && !Objects.equals(source.projectId(), target.projectId());
    }

    private Concept typeOf(Long conceptId) {
        if (conceptId == null) return null;
        return conceptRepository.findById(conceptId).orElseThrow(() ->
                new RelationLinkException(Reason.TYPE_NOT_ALLOWED, "Type inconnu : " + conceptId));
    }

    /**
     * Ties the two types by the definition's correspondence: a type chosen on one side gives the other side's when it
     * is missing, and an imposed correspondence refuses a different one. A definition without correspondence leaves
     * the types free.
     */
    private Concept[] applyMapping(RelationDefinition definition, Concept sourceType, Concept targetType) {
        List<RelationTypeMapping> mappings = mappingRepository.findByDefinition(definition);
        if (mappings.isEmpty()) return new Concept[]{sourceType, targetType};

        if (sourceType != null) {
            Long sourceId = sourceType.getId();
            RelationTypeMapping mapping = mappings.stream()
                    .filter(m -> Objects.equals(m.getSourceType().getId(), sourceId)).findFirst()
                    .orElseThrow(() -> new RelationLinkException(Reason.TYPE_NOT_ALLOWED, "Type non accepté de ce côté de la relation"));
            if (targetType == null) {
                return new Concept[]{sourceType, mapping.getTargetType()};
            }
            if (mapping.getMode() == RelationMappingMode.IMPOSED
                    && !Objects.equals(mapping.getTargetType().getId(), targetType.getId())) {
                throw new RelationLinkException(Reason.TYPE_MISMATCH, "Les deux types ne se correspondent pas");
            }
            return new Concept[]{sourceType, targetType};
        }
        if (targetType != null) {
            Long targetId = targetType.getId();
            RelationTypeMapping mapping = mappings.stream()
                    .filter(m -> Objects.equals(m.getTargetType().getId(), targetId)).findFirst()
                    .orElseThrow(() -> new RelationLinkException(Reason.TYPE_NOT_ALLOWED, "Type non accepté de ce côté de la relation"));
            return new Concept[]{mapping.getSourceType(), targetType};
        }
        return new Concept[]{null, null};
    }

    private void requireNotYetLinked(RelationDefinition definition, RelationEndpoint source, RelationEndpoint target) {
        boolean duplicate = linkQueries.linksBetween(List.of(definition), source.kind(), source.id(), target.kind(), target.id())
                .stream().anyMatch(l -> l.sourceKind().orElse(null) == source.kind()
                        && l.sourceId().orElse(null) != null && l.sourceId().get().equals(source.id()));
        if (duplicate) {
            throw new RelationLinkException(Reason.DUPLICATE, "Ces deux entités sont déjà reliées par cette relation");
        }
    }

    /** Blocking rules refuse the write; the warnings of the others come back with the stored link. */
    private List<RuleViolation> checkRules(RelationDefinition definition, RelationEndpoint source, RelationEndpoint target) {
        List<RuleViolation> blocking = new ArrayList<>();
        List<RuleViolation> warnings = new ArrayList<>();
        for (RelationRule rule : ruleRepository.findByDefinitionsContaining(definition)) {
            String message = violationOf(rule, definition, source, target);
            if (message == null) continue;
            RuleViolation violation = new RuleViolation(rule.getKind(), rule.getSeverity(), message);
            (rule.getSeverity() == RelationRuleSeverity.BLOCKING ? blocking : warnings).add(violation);
        }
        if (!blocking.isEmpty()) {
            throw new RelationLinkException(Reason.RULE_VIOLATION, blocking.get(0).message(), blocking);
        }
        return warnings;
    }

    /** The message of the rule this link would break, or {@code null}. */
    private String violationOf(RelationRule rule, RelationDefinition definition, RelationEndpoint source, RelationEndpoint target) {
        return switch (rule.getKind()) {
            case NO_CYCLE -> source.kind() == target.kind()
                    && linkQueries.reaches(rule.getDefinitions().stream().map(RelationDefinition::getId).toList(),
                    source.kind(), target.id(), source.id())
                    ? "Ce lien créerait un cycle" : null;
            case PAIR_CARDINALITY -> {
                int max = rule.getMaxCount() != null ? rule.getMaxCount() : 1;
                yield linkQueries.linksBetween(rule.getDefinitions(), source.kind(), source.id(), target.kind(), target.id()).size() >= max
                        ? "Ces deux entités ne peuvent avoir que " + max + " lien(s) entre elles" : null;
            }
            case ENTITY_CARDINALITY -> {
                int max = rule.getMaxCount() != null ? rule.getMaxCount() : 1;
                yield linkQueries.countAsSource(definition, source.kind(), source.id()) >= max
                        ? "Cette entité ne peut avoir que " + max + " lien(s) pour cette relation" : null;
            }
            case EXCLUSION -> {
                Set<RelationDefinition> others = new java.util.HashSet<>(rule.getDefinitions());
                others.removeIf(d -> Objects.equals(d.getId(), definition.getId()));
                yield !others.isEmpty()
                        && !linkQueries.linksBetween(others, source.kind(), source.id(), target.kind(), target.id()).isEmpty()
                        ? "Ces deux entités sont déjà reliées par une relation exclusive de celle-ci" : null;
            }
        };
    }
}
