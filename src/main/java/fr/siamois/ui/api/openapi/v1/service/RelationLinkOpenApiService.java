package fr.siamois.ui.api.openapi.v1.service;

import fr.siamois.domain.models.relation.RelationDefinition;
import fr.siamois.domain.models.relation.RelationDefinitionKind;
import fr.siamois.domain.models.relation.RelationLink;
import fr.siamois.domain.models.relation.RelationMirror;
import fr.siamois.domain.models.relation.RelationTypeMapping;
import fr.siamois.domain.models.relation.RelationSide;
import fr.siamois.domain.services.relation.RelationLinkAttributesService;
import fr.siamois.domain.services.relation.RelationLinkException;
import fr.siamois.domain.services.relation.RelationLinkService;
import fr.siamois.ui.api.openapi.v1.request.relation.RelationEndRequest;
import fr.siamois.ui.api.openapi.v1.request.relation.RelationLinkCreateRequest;
import fr.siamois.ui.api.openapi.v1.request.relation.RelationLinkUpdateRequest;
import fr.siamois.ui.api.openapi.v1.resource.form.ResourceRef;
import fr.siamois.ui.api.openapi.v1.response.relation.RelationDefinitionResponse;
import fr.siamois.ui.api.openapi.v1.response.relation.RelationLinkResponse;
import fr.siamois.infrastructure.database.repositories.relation.RelationDefinitionRepository;
import fr.siamois.infrastructure.database.repositories.relation.RelationTypeMappingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** The REST face of {@link RelationLinkService}: request and response shapes, nothing of the rules. */
@Service
@RequiredArgsConstructor
public class RelationLinkOpenApiService {

    private final RelationLinkService relationLinkService;
    private final RelationLinkAttributesService attributesService;
    private final RelationDefinitionRepository definitionRepository;
    private final RelationTypeMappingRepository mappingRepository;

    @Transactional(rollbackFor = Exception.class)
    public RelationLinkResponse create(RelationLinkCreateRequest body, ProjectApiCaller caller) {
        if (body == null || body.getRelation() == null || body.getSource() == null || body.getTarget() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "relation, source et target sont obligatoires");
        }
        RelationEndRequest source = body.getSource();
        RelationEndRequest target = body.getTarget();
        RelationLinkService.Result result = relationLinkService.create(new RelationLinkService.CreateRequest(
                body.getRelation(),
                RelationResourceTypes.kindOf(source.getResourceType()), source.getResourceId(),
                RelationResourceTypes.kindOf(target.getResourceType()), target.getResourceId(),
                body.getSourceType(), body.getTargetType(), sideOf(body.getEditedFrom()),
                attributesOf(body.getAttributes())), caller.person(), caller.accessibleInstitutionIds());
        return responseOf(result);
    }

    @Transactional(rollbackFor = Exception.class)
    public RelationLinkResponse update(Long id, RelationLinkUpdateRequest body, ProjectApiCaller caller) {
        if (body == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Corps de requête obligatoire");
        }
        RelationLinkService.Result result = relationLinkService.update(id, new RelationLinkService.UpdateRequest(
                body.isChangeTypes(), body.getSourceType(), body.getTargetType(), sideOf(body.getEditedFrom()),
                attributesOf(body.getAttributes())), caller.person(), caller.accessibleInstitutionIds());
        return responseOf(result);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id, String editedFrom, ProjectApiCaller caller) {
        relationLinkService.delete(id, sideOf(editedFrom), caller.person(), caller.accessibleInstitutionIds());
    }

    /** What a relation accepts on each side and how its types correspond — all a client needs to offer and prefill types. */
    @Transactional(readOnly = true)
    public RelationDefinitionResponse definition(String code) {
        RelationDefinition definition = definitionRepository.findByCode(code)
                .orElseThrow(() -> new RelationLinkException(RelationLinkException.Reason.DEFINITION_NOT_FOUND,
                        "Relation inconnue : " + code));
        List<RelationDefinitionResponse.TypeMapping> mappings = mappingRepository.findByDefinition(definition).stream()
                .map(RelationLinkOpenApiService::mappingOf).toList();
        return new RelationDefinitionResponse(definition.getCode(), mirrorOf(definition.getMirror()),
                definition.isMirrorEditable(),
                acceptedOf(definition.kindsOf(RelationSide.SOURCE)), acceptedOf(definition.kindsOf(RelationSide.TARGET)),
                mappings);
    }

    private static List<RelationDefinitionResponse.Accepted> acceptedOf(List<RelationDefinitionKind> kinds) {
        return kinds.stream().map(k -> new RelationDefinitionResponse.Accepted(RelationResourceTypes.typeOf(k.getEntityKind()),
                k.getTypeConcept() != null ? k.getTypeConcept().getId() : null)).toList();
    }

    private static RelationDefinitionResponse.TypeMapping mappingOf(RelationTypeMapping m) {
        return new RelationDefinitionResponse.TypeMapping(m.getSourceType().getId(), m.getTargetType().getId(),
                m.getMode().name().toLowerCase());
    }

    private static String mirrorOf(RelationMirror mirror) {
        return switch (mirror) {
            case NONE -> "none";
            case SELF -> "self";
            case OTHER -> "other";
        };
    }

    private RelationLinkResponse responseOf(RelationLinkService.Result result) {
        RelationLink link = result.link();
        Map<String, Object> attributes = new HashMap<>();
        attributesService.read(link).forEach((fieldId, value) -> attributes.put(String.valueOf(fieldId), value));
        List<RelationLinkResponse.Warning> warnings = result.warnings().isEmpty() ? null : result.warnings().stream()
                .map(w -> new RelationLinkResponse.Warning(w.rule().name(), w.message())).toList();
        return new RelationLinkResponse(link.getId(), link.getDefinition().getCode(),
                new ResourceRef(String.valueOf(link.sourceId().orElseThrow()),
                        RelationResourceTypes.typeOf(link.sourceKind().orElseThrow()), null),
                new ResourceRef(String.valueOf(link.targetId().orElseThrow()),
                        RelationResourceTypes.typeOf(link.targetKind().orElseThrow()), null),
                link.getSourceType() != null ? link.getSourceType().getId() : null,
                link.getTargetType() != null ? link.getTargetType().getId() : null,
                attributes, warnings);
    }

    private static RelationSide sideOf(String value) {
        if (value == null || value.equalsIgnoreCase("source")) return RelationSide.SOURCE;
        if (value.equalsIgnoreCase("target")) return RelationSide.TARGET;
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "editedFrom doit valoir source ou target");
    }

    private static Map<Long, Object> attributesOf(Map<String, Object> attributes) {
        if (attributes == null) return null;
        Map<Long, Object> out = new HashMap<>();
        for (Map.Entry<String, Object> entry : attributes.entrySet()) {
            try {
                out.put(Long.valueOf(entry.getKey()), entry.getValue());
            } catch (NumberFormatException e) {
                throw new RelationLinkException(RelationLinkException.Reason.INVALID_ATTRIBUTE,
                        "Identifiant de champ invalide : " + entry.getKey());
            }
        }
        return out;
    }
}
