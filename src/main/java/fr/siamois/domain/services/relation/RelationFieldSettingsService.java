package fr.siamois.domain.services.relation;

import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.domain.models.form.customfield.CustomField;
import fr.siamois.domain.models.form.customfield.CustomFieldRelation;
import fr.siamois.domain.models.relation.RelationDefinition;
import fr.siamois.domain.models.relation.RelationEndKind;
import fr.siamois.domain.models.relation.RelationKind;
import fr.siamois.domain.models.relation.RelationRule;
import fr.siamois.domain.models.relation.RelationSide;
import fr.siamois.domain.models.settings.tableconfig.ConfigurableTable;
import fr.siamois.domain.models.settings.tableconfig.FieldOptions;
import fr.siamois.domain.models.settings.tableconfig.FieldType;
import fr.siamois.domain.services.settings.tableconfig.TableFieldConfigService;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.infrastructure.database.repositories.actionunit.ActionUnitRepository;
import fr.siamois.infrastructure.database.repositories.relation.RelationDefinitionRepository;
import fr.siamois.infrastructure.database.repositories.relation.RelationLinkRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The relation half of a project's field configuration drawer (docs/modele-relations.md, §3): a relation field is
 * created there with its definition, and its identity and the project's rules are read and changed there. The
 * drawer of a project always edits that project's rules: the first change copies the rules it inherited.
 */
@Service
@RequiredArgsConstructor
public class RelationFieldSettingsService {

    private final TableFieldConfigService tableFieldConfigService;
    private final RelationDefinitionService definitionService;
    private final RelationRuleService ruleService;
    private final RelationDefinitionRepository definitionRepository;
    private final RelationLinkRepository linkRepository;
    private final RelationConfigAuthorizer configAuthorizer;
    private final ActionUnitRepository actionUnitRepository;

    /**
     * What the drawer asks for a new relation field.
     *
     * @param otherKinds  the records the field can link (the other side); none: any record
     * @param mirrorLabel the label of the field of the other side, created but placed on no form; ignored when
     *                    symmetric
     */
    public record Draft(RelationKind kind, boolean symmetric, boolean hierarchical, @Nullable String mirrorLabel,
                        List<RelationEndKind> otherKinds) {
        public Draft {
            otherKinds = otherKinds == null ? List.of() : List.copyOf(otherKinds);
        }
    }

    /**
     * A relation field as the drawer shows it in one project.
     *
     * @param side             the field's side; SOURCE for a symmetric relation
     * @param otherField       the field of the other side (itself when symmetric)
     * @param linkCount        the links of the relation, in every project: with any, its identity no longer changes
     * @param rules            the rules the project follows
     * @param ownRules         the project has its own rules (else it inherits them)
     * @param rulesOrigin      where inherited rules come from: "project", "institution", "platform" or "none"
     * @param identityEditable the caller may change the identity (and there is no link yet)
     */
    public record View(RelationDefinition definition, RelationSide side, CustomField otherField, long linkCount,
                       RelationRuleSet rules, boolean ownRules, String rulesOrigin, boolean identityEditable,
                       boolean system) {

        public boolean symmetric() {
            return definition.isSymmetric();
        }

        public boolean complex() {
            return !definition.isSimple();
        }

        /** The kinds the field's own side accepts, and the other side's. */
        public List<RelationEndKind> kindsOf(RelationSide of) {
            return rules.kindsOf(of).stream().map(RelationRuleSet.AcceptedKind::kind).distinct().toList();
        }
    }

    /** The kind of record a configurable table holds. */
    public static RelationEndKind kindOf(ConfigurableTable table) {
        return switch (table) {
            case UE -> RelationEndKind.RECORDING_UNIT;
            case MOBILIER -> RelationEndKind.SPECIMEN;
            case PHASE -> RelationEndKind.PHASE;
            case CONTENANT -> RelationEndKind.CONTAINER;
            case DOCUMENT -> RelationEndKind.DOCUMENT;
            case STUDY -> RelationEndKind.STUDY;
            case PROJECT -> RelationEndKind.ACTION_UNIT;
        };
    }

    /**
     * Creates a relation field on a type's form, with its definition, the field of the other side (on no form) and
     * the relation's first rules, in one transaction: any refusal leaves nothing behind. A complex relation is
     * qualified by the field itself, whose vocabulary is configured like any other.
     */
    @Transactional(rollbackFor = Exception.class)
    public RelationDefinition createRelationField(long projectId, ConfigurableTable table, String typeName,
                                                  String name, @Nullable String description, Draft draft,
                                                  PersonDTO caller) {
        tableFieldConfigService.createField(projectId, table, typeName, name, FieldType.RELATION, description,
                FieldOptions.NONE);
        CustomField field = tableFieldConfigService.findField(projectId, table, typeName, name)
                .orElseThrow(() -> new IllegalStateException("The relation field just created was not found: " + name));
        boolean complex = draft.kind() == RelationKind.COMPLEX;
        return definitionService.create(new RelationDefinitionService.CreateRequest(field.getId(), null,
                draft.symmetric() ? null : draft.mirrorLabel(), draft.kind(), draft.symmetric(), draft.hierarchical(),
                complex ? field.getId() : null,
                new RelationRuleService.RuleSpec(draft.hierarchical(), false, false,
                        acceptedKinds(kindOf(table), draft), Map.of(), List.of())), projectId, caller);
    }

    /** The relation a field is a side of, as one project sees it; empty for a field of no relation. */
    @Transactional(readOnly = true)
    public Optional<View> view(long fieldId, long projectId, PersonDTO caller) {
        return definitionRepository.findByField(fieldId).map(definition -> {
            RelationSide side = definition.isSymmetric() || definition.getSourceField().getId().equals(fieldId)
                    ? RelationSide.SOURCE : RelationSide.TARGET;
            CustomField other = side == RelationSide.SOURCE ? definition.getTargetField() : definition.getSourceField();
            RelationRuleSet rules = ruleService.rulesFor(definition.getId(), projectId);
            boolean own = ruleService.ownRules(definition.getId(), scopeOf(projectId)).isPresent();
            long links = linkRepository.countByDefinitionId(definition.getId());
            boolean system = isSystem(definition.getSourceField()) || isSystem(definition.getTargetField());
            boolean editable = links == 0 && configAuthorizer.canEditDefinition(system, projectId, caller);
            return new View(definition, side, other, links, rules, own, originOf(rules.rule()), editable, system);
        });
    }

    /** Changes the identity of a relation that has no link yet. */
    @Transactional(rollbackFor = Exception.class)
    public RelationDefinition saveIdentity(long definitionId, RelationKind kind, boolean hierarchical, long projectId,
                                           PersonDTO caller) {
        RelationDefinition definition = definitionRepository.findById(definitionId).orElseThrow();
        Long qualifier = kind == RelationKind.COMPLEX ? qualifierOf(definition) : null;
        return definitionService.updateIdentity(definitionId,
                new RelationDefinitionService.IdentityUpdate(kind, hierarchical, qualifier), projectId, caller);
    }

    /** Saves the project's own rules: the first time, the project stops inheriting them. */
    @Transactional(rollbackFor = Exception.class)
    public RelationRuleSet saveRules(long definitionId, long projectId, RelationRuleService.RuleSpec spec,
                                     PersonDTO caller) {
        return ruleService.save(definitionId, scopeOf(projectId), spec, caller);
    }

    /** Drops the project's own rules: it follows its institution's, or the platform default, again. */
    @Transactional(rollbackFor = Exception.class)
    public void resetRules(long definitionId, long projectId, PersonDTO caller) {
        ruleService.delete(definitionId, scopeOf(projectId), caller);
    }

    /** The other relations, which a rule can declare exclusive with this one. */
    @Transactional(readOnly = true)
    public List<RelationDefinition> otherDefinitions(long definitionId) {
        List<RelationDefinition> others = new ArrayList<>();
        definitionRepository.findAll().forEach(d -> {
            if (d.getId() != definitionId) others.add(d);
        });
        return others;
    }

    /** The source kind is the table's; a symmetric relation lists every kind on its one side. */
    private static List<RelationRuleSet.AcceptedKind> acceptedKinds(RelationEndKind ownKind, Draft draft) {
        List<RelationRuleSet.AcceptedKind> kinds = new ArrayList<>();
        if (draft.symmetric()) {
            if (draft.otherKinds().isEmpty()) return kinds;
            kinds.add(new RelationRuleSet.AcceptedKind(RelationSide.SOURCE, ownKind, null));
            draft.otherKinds().stream().filter(k -> k != ownKind)
                    .forEach(k -> kinds.add(new RelationRuleSet.AcceptedKind(RelationSide.SOURCE, k, null)));
            return kinds;
        }
        kinds.add(new RelationRuleSet.AcceptedKind(RelationSide.SOURCE, ownKind, null));
        draft.otherKinds().forEach(k -> kinds.add(new RelationRuleSet.AcceptedKind(RelationSide.TARGET, k, null)));
        return kinds;
    }

    /** A complex relation keeps its qualifier field; one that becomes complex is qualified by its source field. */
    private static Long qualifierOf(RelationDefinition definition) {
        return definition.getQualifierField() != null ? definition.getQualifierField().getId()
                : definition.getSourceField().getId();
    }

    private RelationScope scopeOf(long projectId) {
        ActionUnit project = actionUnitRepository.findById(projectId).orElseThrow();
        return RelationScope.project(project.getCreatedByInstitution().getId(), projectId);
    }

    private static String originOf(@Nullable RelationRule rule) {
        if (rule == null) return "none";
        if (rule.getActionUnit() != null) return "project";
        if (rule.getInstitution() != null) return "institution";
        return "platform";
    }

    private static boolean isSystem(@Nullable CustomField field) {
        return field != null && Boolean.TRUE.equals(field.getIsSystemField());
    }

    /** Whether a field is a relation field the drawer can create (not a system one). */
    public static boolean isRelationField(@Nullable CustomField field) {
        return field instanceof CustomFieldRelation;
    }
}
