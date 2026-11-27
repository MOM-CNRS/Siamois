package fr.siamois.ui.api.openapi.v1.service;

import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.domain.models.relation.RelationDefinition;
import fr.siamois.domain.models.relation.RelationLink;
import fr.siamois.domain.models.relation.RelationRule;
import fr.siamois.domain.models.relation.RelationSide;
import fr.siamois.domain.services.relation.RelationDefinitionService;
import fr.siamois.domain.services.relation.RelationLinkException;
import fr.siamois.domain.services.relation.RelationLinkService;
import fr.siamois.domain.services.relation.RelationRuleService;
import fr.siamois.domain.services.relation.RelationRuleSet;
import fr.siamois.domain.services.relation.RelationScope;
import fr.siamois.domain.services.relation.RuleViolation;
import fr.siamois.infrastructure.database.repositories.actionunit.ActionUnitRepository;
import fr.siamois.infrastructure.database.repositories.relation.RelationDefinitionRepository;
import fr.siamois.infrastructure.database.repositories.relation.RelationLinkRepository;
import fr.siamois.ui.api.openapi.v1.request.relation.RelationDefinitionCreateRequest;
import fr.siamois.ui.api.openapi.v1.request.relation.RelationDefinitionUpdateRequest;
import fr.siamois.ui.api.openapi.v1.request.relation.RelationEndRequest;
import fr.siamois.ui.api.openapi.v1.request.relation.RelationLinkCreateRequest;
import fr.siamois.ui.api.openapi.v1.request.relation.RelationLinkUpdateRequest;
import fr.siamois.ui.api.openapi.v1.request.relation.RelationRulesRequest;
import fr.siamois.ui.api.openapi.v1.response.relation.RelationDefinitionResponse;
import fr.siamois.ui.api.openapi.v1.response.relation.RelationEndResponse;
import fr.siamois.ui.api.openapi.v1.response.relation.RelationFieldResponse;
import fr.siamois.ui.api.openapi.v1.response.relation.RelationLinkResponse;
import fr.siamois.ui.api.openapi.v1.response.relation.RelationRulesResponse;
import fr.siamois.ui.api.openapi.v1.response.relation.RuleViolationResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static fr.siamois.ui.api.openapi.v1.service.RelationResourceTypes.kindOf;
import static fr.siamois.ui.api.openapi.v1.service.RelationResourceTypes.nameOf;
import static fr.siamois.ui.api.openapi.v1.service.RelationResourceTypes.relationKindOf;
import static fr.siamois.ui.api.openapi.v1.service.RelationResourceTypes.sideOf;
import static fr.siamois.ui.api.openapi.v1.service.RelationResourceTypes.typeOf;

/** The REST face of the configurable relations: request and response shapes, nothing of the rules. */
@Service
@RequiredArgsConstructor
public class RelationOpenApiService {

    private final RelationLinkService linkService;
    private final RelationDefinitionService definitionService;
    private final RelationRuleService ruleService;
    private final RelationDefinitionRepository definitionRepository;
    private final RelationLinkRepository linkRepository;
    private final ActionUnitRepository actionUnitRepository;

    // ---- links ----

    @Transactional(rollbackFor = Exception.class)
    public RelationLinkResponse createLink(RelationLinkCreateRequest body, ProjectApiCaller caller) {
        if (body == null || (body.field() == null && body.definition() == null) || body.edited() == null
                || body.other() == null) {
            throw badRequest("field (ou definition), record et other sont obligatoires");
        }
        RelationDefinition fieldDefinition = body.field() != null ? definitionOfField(body.field()) : null;
        RelationLinkService.Result result = linkService.create(new RelationLinkService.CreateRequest(
                fieldDefinition != null ? fieldDefinition.getId() : body.definition(),
                fieldDefinition != null ? sideOfField(fieldDefinition, body.field()) : sideOf(body.side()),
                end(body.edited()), end(body.other()),
                body.qualifier(), Boolean.TRUE.equals(body.uncertain())), caller.person(), caller.accessibleInstitutionIds());
        return responseOf(result);
    }

    @Transactional(rollbackFor = Exception.class)
    public RelationLinkResponse updateLink(Long id, RelationLinkUpdateRequest body, ProjectApiCaller caller) {
        if (body == null) throw badRequest("Corps de requête obligatoire");
        RelationSide side = body.field() != null ? sideOfField(definitionOfField(body.field()), body.field())
                : sideOf(body.side());
        RelationLinkService.Result result = linkService.update(id, new RelationLinkService.UpdateRequest(
                side, body.qualifier(), Boolean.TRUE.equals(body.uncertain())),
                caller.person(), caller.accessibleInstitutionIds());
        return responseOf(result);
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteLink(Long id, @Nullable Long field, @Nullable String side, ProjectApiCaller caller) {
        RelationSide edited = field != null ? sideOfField(definitionOfField(field), field) : sideOf(side);
        linkService.delete(id, edited, caller.person(), caller.accessibleInstitutionIds());
    }

    /** The definition a relation field is a side of. */
    private RelationDefinition definitionOfField(Long fieldId) {
        return definitionRepository.findByField(fieldId).orElseThrow(() ->
                new RelationLinkException(RelationLinkException.Reason.DEFINITION_NOT_FOUND,
                        "Ce champ n'est un côté d'aucune relation : " + fieldId));
    }

    /** The source side for a definition's source field (and for a symmetric one), the target side otherwise. */
    private static RelationSide sideOfField(RelationDefinition definition, Long fieldId) {
        return definition.isSymmetric() || definition.getSourceField().getId().equals(fieldId)
                ? RelationSide.SOURCE : RelationSide.TARGET;
    }

    /** What a relation field is in a project: its side, the records it can link, its qualifiers seen from its side. */
    @Transactional(readOnly = true)
    public RelationFieldResponse field(Long fieldId, Long projectId, ProjectApiCaller caller) {
        RelationDefinition definition = definitionRepository.findByField(fieldId).orElseThrow(() ->
                new RelationLinkException(RelationLinkException.Reason.DEFINITION_NOT_FOUND,
                        "Ce champ n'est un côté d'aucune relation : " + fieldId));
        requireReachable(projectId, caller);
        RelationRuleSet rules = ruleService.rulesFor(definition.getId(), projectId);
        boolean isSource = definition.isSymmetric() || definition.getSourceField().getId().equals(fieldId);
        RelationSide side = isSource ? RelationSide.SOURCE : RelationSide.TARGET;
        RelationSide other = isSource ? RelationSide.TARGET : RelationSide.SOURCE;
        boolean singleChoice;
        if (definition.isSymmetric()) {
            singleChoice = rules.sourceUnique() || rules.targetUnique();
        } else {
            singleChoice = isSource ? rules.sourceUnique() : rules.targetUnique();
        }
        List<RelationFieldResponse.AcceptedEnd> accepts = rules.kindsOf(definition.isSymmetric() ? side : other).stream()
                .map(k -> new RelationFieldResponse.AcceptedEnd(typeOf(k.kind()), k.subtypeId()))
                .toList();
        List<RelationFieldResponse.Qualifier> qualifiers = new ArrayList<>();
        boolean readsStored = isSource || definition.isSymmetric();
        rules.mirrorByConcept().forEach((concept, mirror) ->
                qualifiers.add(new RelationFieldResponse.Qualifier(readsStored ? concept : mirror, concept)));
        qualifiers.sort((a, b) -> Long.compare(a.stored(), b.stored()));
        Long mirrorField = isSource ? definition.getTargetField().getId() : definition.getSourceField().getId();
        return new RelationFieldResponse(fieldId, definitionOf(definition), nameOf(side), mirrorField, singleChoice,
                accepts, qualifiers, rules.forbidsCycles());
    }

    // ---- definitions ----

    @Transactional(readOnly = true)
    public RelationDefinitionResponse definition(Long id) {
        return definitionOf(definitionRepository.findById(id).orElseThrow(() ->
                new RelationLinkException(RelationLinkException.Reason.DEFINITION_NOT_FOUND, "Relation inconnue : " + id)));
    }

    @Transactional(rollbackFor = Exception.class)
    public RelationDefinitionResponse createDefinition(RelationDefinitionCreateRequest body, ProjectApiCaller caller) {
        if (body == null || body.sourceField() == null || body.kind() == null) {
            throw badRequest("sourceField et kind sont obligatoires");
        }
        RelationDefinition created = definitionService.create(new RelationDefinitionService.CreateRequest(
                body.sourceField(), body.targetField(), body.targetFieldLabel(), relationKindOf(body.kind()),
                Boolean.TRUE.equals(body.symmetric()), Boolean.TRUE.equals(body.hierarchical()), body.qualifierField(),
                specOf(body.defaultRules())), caller.person());
        return definitionOf(created);
    }

    @Transactional(rollbackFor = Exception.class)
    public RelationDefinitionResponse updateDefinition(Long id, RelationDefinitionUpdateRequest body,
                                                       ProjectApiCaller caller) {
        if (body == null || body.kind() == null) throw badRequest("kind est obligatoire");
        return definitionOf(definitionService.updateIdentity(id, new RelationDefinitionService.IdentityUpdate(
                relationKindOf(body.kind()), Boolean.TRUE.equals(body.hierarchical()), body.qualifierField()),
                caller.person()));
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteDefinition(Long id, ProjectApiCaller caller) {
        definitionService.delete(id, caller.person());
    }

    // ---- rules ----

    /** The rules a scope reads: its own when it has some, else those it inherits. */
    @Transactional(readOnly = true)
    public RelationRulesResponse rules(Long definitionId, @Nullable Long institutionId, @Nullable Long projectId,
                                       ProjectApiCaller caller) {
        RelationScope scope = scopeOf(institutionId, projectId, caller);
        var own = ruleService.ownRules(definitionId, scope);
        if (own.isPresent()) return rulesOf(definitionId, own.get(), true);
        RelationRuleSet inherited = scope.projectId() != null
                ? ruleService.rulesFor(definitionId, scope.projectId())
                : ruleService.ownRules(definitionId, RelationScope.platform()).orElse(null);
        return inherited != null ? rulesOf(definitionId, inherited, false)
                : new RelationRulesResponse(definitionId, "none", false, false, false, false, List.of(), List.of(),
                List.of());
    }

    @Transactional(rollbackFor = Exception.class)
    public RelationRulesResponse saveRules(Long definitionId, @Nullable Long institutionId, @Nullable Long projectId,
                                           RelationRulesRequest body, ProjectApiCaller caller) {
        RelationScope scope = scopeOf(institutionId, projectId, caller);
        return rulesOf(definitionId, ruleService.save(definitionId, scope, specOf(body), caller.person()), true);
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteRules(Long definitionId, @Nullable Long institutionId, @Nullable Long projectId,
                            ProjectApiCaller caller) {
        ruleService.delete(definitionId, scopeOf(institutionId, projectId, caller), caller.person());
    }

    // ---- mapping ----

    private RelationLinkResponse responseOf(RelationLinkService.Result result) {
        RelationLink link = result.link();
        Long qualifier = link.getQualifier() != null ? link.getQualifier().getId() : null;
        Long mirror = qualifier != null
                ? ruleService.rulesFor(link.getDefinition().getId(), link.getProjectId()).mirrorOf(qualifier)
                : null;
        return new RelationLinkResponse(link.getId(), link.getDefinition().getId(),
                new RelationEndResponse(typeOf(link.getSourceKind()), link.getSourceId()),
                new RelationEndResponse(typeOf(link.getTargetKind()), link.getTargetId()),
                qualifier, mirror, link.isUncertain(),
                result.warnings().stream().map(RelationOpenApiService::violationOf).toList());
    }

    public static RuleViolationResponse violationOf(RuleViolation violation) {
        return new RuleViolationResponse(violation.rule().name(), violation.blocking(), violation.message(),
                violation.linkIds());
    }

    private RelationDefinitionResponse definitionOf(RelationDefinition definition) {
        return new RelationDefinitionResponse(definition.getId(), definition.getSourceField().getId(),
                definition.getTargetField().getId(), nameOf(definition.getKind()), definition.isSymmetric(),
                definition.isHierarchical(),
                definition.getQualifierField() != null ? definition.getQualifierField().getId() : null,
                linkRepository.existsByDefinitionId(definition.getId()));
    }

    private static RelationRulesResponse rulesOf(Long definitionId, RelationRuleSet rules, boolean own) {
        RelationRule rule = rules.rule();
        String scope;
        if (rule == null) {
            scope = "none";
        } else if (rule.getActionUnit() != null) {
            scope = "project";
        } else if (rule.getInstitution() != null) {
            scope = "institution";
        } else {
            scope = "platform";
        }
        Map<Long, Long> sorted = new LinkedHashMap<>();
        rules.mirrorByConcept().entrySet().stream().sorted(Map.Entry.comparingByKey())
                .forEach(e -> sorted.put(e.getKey(), e.getValue()));
        return new RelationRulesResponse(definitionId, scope, own, rules.noCycle(), rules.sourceUnique(),
                rules.targetUnique(),
                rules.kinds().stream().map(k -> new RelationRulesRequest.AcceptedKind(nameOf(k.side()), typeOf(k.kind()),
                        k.subtypeId())).toList(),
                sorted.entrySet().stream().map(e -> new RelationRulesRequest.MirrorPair(e.getKey(), e.getValue())).toList(),
                rules.excludedDefinitionIds());
    }

    private static RelationRuleService.RuleSpec specOf(@Nullable RelationRulesRequest body) {
        if (body == null) return new RelationRuleService.RuleSpec(false, false, false, List.of(), Map.of(), List.of());
        List<RelationRuleSet.AcceptedKind> kinds = body.accepted() == null ? List.of() : body.accepted().stream()
                .map(a -> new RelationRuleSet.AcceptedKind(sideOf(a.side()), kindOf(a.resourceType()), a.subtype()))
                .toList();
        Map<Long, Long> mirrors = new LinkedHashMap<>();
        if (body.mirrors() != null) {
            for (RelationRulesRequest.MirrorPair pair : body.mirrors()) {
                if (pair.concept() == null || pair.mirror() == null || mirrors.put(pair.concept(), pair.mirror()) != null) {
                    throw badRequest("Paire miroir incomplète ou en double");
                }
            }
        }
        return new RelationRuleService.RuleSpec(Boolean.TRUE.equals(body.noCycle()),
                Boolean.TRUE.equals(body.sourceUnique()), Boolean.TRUE.equals(body.targetUnique()), kinds, mirrors,
                body.exclusions() == null ? List.of() : body.exclusions());
    }

    /** A project names its own institution; an institution must be one the caller belongs to. */
    private RelationScope scopeOf(@Nullable Long institutionId, @Nullable Long projectId, ProjectApiCaller caller) {
        if (projectId != null) {
            ActionUnit project = requireReachable(projectId, caller);
            if (institutionId != null && !institutionId.equals(project.getCreatedByInstitution().getId())) {
                throw badRequest("Ce projet n'appartient pas à cette institution");
            }
            return RelationScope.project(project.getCreatedByInstitution().getId(), projectId);
        }
        if (institutionId != null) {
            if (!caller.accessibleInstitutionIds().contains(institutionId)) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Institution introuvable : " + institutionId);
            }
            return RelationScope.institution(institutionId);
        }
        return RelationScope.platform();
    }

    private ActionUnit requireReachable(Long projectId, ProjectApiCaller caller) {
        if (projectId == null) throw badRequest("projectId est obligatoire");
        return actionUnitRepository.findById(projectId)
                .filter(p -> p.getCreatedByInstitution() != null
                        && caller.accessibleInstitutionIds().contains(p.getCreatedByInstitution().getId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Projet introuvable : " + projectId));
    }

    private static RelationLinkService.End end(RelationEndRequest end) {
        if (end.resourceType() == null || end.resourceId() == null) {
            throw badRequest("resourceType et resourceId sont obligatoires");
        }
        return new RelationLinkService.End(kindOf(end.resourceType()), end.resourceId());
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
