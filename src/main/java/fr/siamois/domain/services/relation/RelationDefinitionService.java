package fr.siamois.domain.services.relation;

import fr.siamois.domain.models.form.customfield.CustomField;
import fr.siamois.domain.models.form.customfield.CustomFieldRelation;
import fr.siamois.domain.models.form.customfield.vocabulary.CustomFieldSelectOne;
import fr.siamois.domain.models.form.customfield.vocabulary.CustomFieldSelectOneFromFieldCode;
import fr.siamois.domain.models.relation.RelationDefinition;
import fr.siamois.domain.models.relation.RelationKind;
import fr.siamois.domain.services.relation.RelationLinkException.Reason;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.infrastructure.database.repositories.form.CustomFieldRepository;
import fr.siamois.infrastructure.database.repositories.relation.RelationDefinitionRepository;
import fr.siamois.infrastructure.database.repositories.relation.RelationLinkRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

/**
 * The definitions of the relations (docs/modele-relations.md, §3.2 and §4.4), created from the configuration of a
 * relation field: the field becomes the source side, the other side is an existing relation field or a new one
 * (the same field for a symmetric relation), and the definition starts with its platform default rules, in one
 * transaction. A definition's identity no longer changes once it has links, and a definition with links cannot be
 * deleted. Whoever configures a project's fields edits the definitions it configures; those of system fields belong
 * to the platform administrator.
 */
@Service
@RequiredArgsConstructor
public class RelationDefinitionService {

    private final RelationDefinitionRepository definitionRepository;
    private final RelationLinkRepository linkRepository;
    private final CustomFieldRepository fieldRepository;
    private final RelationRuleService ruleService;
    private final RelationConfigAuthorizer configAuthorizer;
    private final RelationFieldIndex fieldIndex;

    /**
     * @param sourceFieldId    the relation field the definition is created from
     * @param targetFieldId    the existing relation field of the other side, or null to create one (ignored when
     *                         symmetric: the source field is both sides)
     * @param targetFieldLabel the label of the field to create for the other side
     * @param qualifierFieldId for a complex relation, the concept field whose value qualifies a link, or null
     * @param defaultRules     the platform default rules the definition starts with
     */
    public record CreateRequest(Long sourceFieldId, @Nullable Long targetFieldId, @Nullable String targetFieldLabel,
                                RelationKind kind, boolean symmetric, boolean hierarchical,
                                @Nullable Long qualifierFieldId, RelationRuleService.RuleSpec defaultRules) {
    }

    /** The identity's options that can change while the definition has no link. */
    public record IdentityUpdate(RelationKind kind, boolean hierarchical, @Nullable Long qualifierFieldId) {
    }

    @Transactional(rollbackFor = Exception.class)
    public RelationDefinition create(CreateRequest request, @Nullable Long projectId, PersonDTO caller) {
        CustomFieldRelation source = freeRelationField(request.sourceFieldId());
        requireCanEdit(Boolean.TRUE.equals(source.getIsSystemField()), projectId, caller);
        CustomFieldRelation target;
        if (request.symmetric()) {
            target = source;
        } else if (request.targetFieldId() != null) {
            if (request.targetFieldId().equals(request.sourceFieldId())) {
                throw invalid("Une relation non symétrique a deux champs différents");
            }
            target = freeRelationField(request.targetFieldId());
        } else {
            target = newRelationField(request.targetFieldLabel());
        }
        if (request.symmetric() && request.hierarchical()) {
            throw invalid("Une relation symétrique ne peut pas être hiérarchique");
        }

        RelationDefinition definition = new RelationDefinition();
        definition.setSourceField(source);
        definition.setTargetField(target);
        definition.setSymmetric(request.symmetric());
        applyIdentity(definition, new IdentityUpdate(request.kind(), request.hierarchical(), request.qualifierFieldId()));
        RelationDefinition saved = definitionRepository.save(definition);
        ruleService.saveWithoutPermission(saved, RelationScope.platform(), request.defaultRules());
        fieldIndex.evict();
        return saved;
    }

    @Transactional(rollbackFor = Exception.class)
    public RelationDefinition updateIdentity(Long definitionId, IdentityUpdate update, @Nullable Long projectId,
                                             PersonDTO caller) {
        RelationDefinition definition = definition(definitionId);
        requireCanEdit(isSystem(definition), projectId, caller);
        boolean changes = definition.getKind() != update.kind() || definition.isHierarchical() != update.hierarchical()
                || !Objects.equals(idOf(definition.getQualifierField()), update.qualifierFieldId());
        if (changes && linkRepository.existsByDefinitionId(definitionId)) {
            throw invalid("Cette relation a déjà des liens : sa nature ne peut plus changer");
        }
        if (definition.isSymmetric() && update.hierarchical()) {
            throw invalid("Une relation symétrique ne peut pas être hiérarchique");
        }
        applyIdentity(definition, update);
        RelationDefinition saved = definitionRepository.save(definition);
        fieldIndex.evict();
        return saved;
    }

    /** Refused while the definition has links: they must be deleted first, explicitly. Its fields stay. */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long definitionId, @Nullable Long projectId, PersonDTO caller) {
        RelationDefinition definition = definition(definitionId);
        requireCanEdit(isSystem(definition), projectId, caller);
        if (linkRepository.existsByDefinitionId(definitionId)) {
            throw invalid("Cette relation a des liens : supprimez-les d'abord");
        }
        ruleService.deleteAllOf(definition);
        definitionRepository.delete(definition);
        fieldIndex.evict();
    }

    private void applyIdentity(RelationDefinition definition, IdentityUpdate update) {
        definition.setKind(update.kind());
        definition.setHierarchical(update.hierarchical());
        Long qualifierFieldId = update.qualifierFieldId();
        if (qualifierFieldId == null) {
            definition.setQualifierField(null);
            return;
        }
        if (update.kind() != RelationKind.COMPLEX) {
            throw invalid("Seule une relation complexe a un qualificatif");
        }
        CustomField qualifier = fieldRepository.findById(qualifierFieldId)
                .orElseThrow(() -> invalid("Champ inconnu : " + qualifierFieldId));
        // the relation's own source field qualifies its links when its vocabulary lists the qualifiers
        boolean itself = definition.getSourceField() != null && qualifierFieldId.equals(definition.getSourceField().getId());
        if (!itself && !(qualifier instanceof CustomFieldSelectOne) && !(qualifier instanceof CustomFieldSelectOneFromFieldCode)) {
            throw invalid("Le qualificatif doit être un champ de vocabulaire à choix unique");
        }
        definition.setQualifierField(qualifier);
    }

    /** A relation field that is not yet a side of a definition. */
    private CustomFieldRelation freeRelationField(Long fieldId) {
        CustomField field = fieldRepository.findById(fieldId)
                .orElseThrow(() -> invalid("Champ inconnu : " + fieldId));
        if (!(field instanceof CustomFieldRelation relationField)) {
            throw invalid("Ce champ n'est pas un champ relation : " + fieldId);
        }
        if (definitionRepository.findByField(fieldId).isPresent()) {
            throw invalid("Ce champ est déjà un côté d'une relation : " + fieldId);
        }
        return relationField;
    }

    private CustomFieldRelation newRelationField(@Nullable String label) {
        if (label == null || label.isBlank()) {
            throw invalid("Le champ de l'autre côté doit avoir un libellé");
        }
        CustomFieldRelation field = new CustomFieldRelation();
        field.setLabel(label.trim());
        field.setIsSystemField(false);
        return fieldRepository.save(field);
    }

    /** A relation between system fields, as the stratigraphy is: its identity is the platform administrator's. */
    private static boolean isSystem(RelationDefinition definition) {
        return isSystem(definition.getSourceField()) || isSystem(definition.getTargetField());
    }

    private static boolean isSystem(@Nullable CustomField field) {
        return field != null && Boolean.TRUE.equals(field.getIsSystemField());
    }

    private void requireCanEdit(boolean system, @Nullable Long projectId, PersonDTO caller) {
        if (!configAuthorizer.canEditDefinition(system, projectId, caller)) {
            throw new RelationLinkException(Reason.FORBIDDEN, "Modification non autorisée");
        }
    }

    private RelationDefinition definition(Long id) {
        return definitionRepository.findById(id).orElseThrow(() ->
                new RelationLinkException(Reason.DEFINITION_NOT_FOUND, "Relation inconnue : " + id));
    }

    private static Long idOf(CustomField field) {
        return field != null ? field.getId() : null;
    }

    private static RelationLinkException invalid(String message) {
        return new RelationLinkException(Reason.INVALID_CONFIGURATION, message);
    }
}
