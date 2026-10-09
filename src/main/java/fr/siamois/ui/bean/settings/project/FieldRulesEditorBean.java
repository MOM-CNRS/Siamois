package fr.siamois.ui.bean.settings.project;

import fr.siamois.domain.models.events.LoginEvent;
import fr.siamois.domain.models.exceptions.vocabulary.NoConfigForFieldException;
import fr.siamois.domain.models.form.customfield.CustomField;
import fr.siamois.domain.models.form.customfield.vocabulary.CustomFieldConcept;
import fr.siamois.domain.models.form.rules.Condition;
import fr.siamois.domain.models.form.rules.ConditionOp;
import fr.siamois.domain.models.form.rules.FieldConstraint;
import fr.siamois.domain.models.form.rules.FieldRules;
import fr.siamois.domain.models.form.rules.FieldValueSpec;
import fr.siamois.domain.models.form.rules.OptionsFilter;
import fr.siamois.domain.models.form.rules.PlaceSourceSpec;
import fr.siamois.domain.models.form.rules.RuleFieldFamily;
import fr.siamois.domain.models.settings.tableconfig.LayoutRow;
import fr.siamois.domain.models.settings.tableconfig.TypeFieldFormConfig;
import fr.siamois.domain.services.form.layout.FormLayoutService;
import fr.siamois.domain.services.form.layout.InvalidRulesException;
import fr.siamois.domain.services.vocabulary.FieldConfigurationService;
import fr.siamois.infrastructure.database.repositories.vocabulary.dto.ConceptAutocompleteDTO;
import fr.siamois.ui.bean.LangBean;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The rules drawer of the table configuration screen: edits, for one field of a type's form, when it
 * is enabled, when it is required, which values it offers and how it orders against other fields.
 * <p>
 * The editor covers the usual shapes: one comparison, or several combined with "all" / "any". A rule
 * built otherwise (nested compositions, another kind of options filter) is kept as it is and shown as
 * such; it can be removed but not edited.
 */
@Slf4j
@Component
@Getter
@Setter
@Scope(value = "session", proxyMode = ScopedProxyMode.TARGET_CLASS)
public class FieldRulesEditorBean implements Serializable {

    /** One comparison of a condition being edited. */
    @Getter
    @Setter
    public static class LeafDraft implements Serializable {
        private Long fieldId;
        private ConditionOp op = ConditionOp.EQ;
        /** Concepts: the chosen concept ids. */
        private List<String> values = new ArrayList<>();
        /** Number, date, text: the single value typed. */
        private String value;
    }

    /** A condition being edited: comparisons combined with all / any, or an advanced rule kept as is. */
    @Getter
    @Setter
    public static class ConditionDraft implements Serializable {
        private boolean any;
        private List<LeafDraft> leaves = new ArrayList<>();
        /** Set when the stored condition is not one this editor can represent. */
        private Condition advanced;

        public boolean isAdvanced() {
            return advanced != null;
        }
    }

    @Getter
    @Setter
    public static class ConstraintDraft implements Serializable {
        private FieldConstraint.Op op = FieldConstraint.Op.GTE;
        private Long fieldId;
    }

    /** A concept offered in a value picker (a class rather than a record: the view reads it by getters). */
    @Getter
    @lombok.AllArgsConstructor
    public static class Choice implements Serializable {
        private final String value;
        private final String label;
    }

    private final transient FormLayoutService formLayoutService;
    private final transient FieldConfigurationService fieldConfigurationService;
    private final transient ProjectTableFieldSettingsBean settingsBean;
    private final LangBean langBean;

    private boolean open;
    private TypeFieldFormConfig field;
    private List<TypeFieldFormConfig> otherFields = new ArrayList<>();
    private ConditionDraft enabledWhen = new ConditionDraft();
    private ConditionDraft requiredWhen = new ConditionDraft();
    private Long optionsFieldId;
    private OptionsFilter advancedOptions;
    private List<PlaceSourceSpec> keptPlaceSources = List.of();
    private List<ConstraintDraft> constraints = new ArrayList<>();
    private String errorMessage;
    private final transient Map<Long, List<Choice>> conceptChoices = new HashMap<>();

    public FieldRulesEditorBean(FormLayoutService formLayoutService,
                                FieldConfigurationService fieldConfigurationService,
                                ProjectTableFieldSettingsBean settingsBean,
                                LangBean langBean) {
        this.formLayoutService = formLayoutService;
        this.fieldConfigurationService = fieldConfigurationService;
        this.settingsBean = settingsBean;
        this.langBean = langBean;
    }

    @EventListener(LoginEvent.class)
    public void reset() {
        close();
    }

    public void close() {
        open = false;
        field = null;
        otherFields = new ArrayList<>();
        errorMessage = null;
        conceptChoices.clear();
        keptPlaceSources = List.of();
    }

    @EventListener(FieldOpenedEvent.class)
    public void onFieldOpened(FieldOpenedEvent event) {
        openFor(event.field());
    }

    /** The field's Save, after its definition: writes the rules when this field's are on screen. */
    public void saveIfOpen() {
        if (open) {
            save();
        }
    }

    public void openFor(TypeFieldFormConfig target) {
        FieldRules rules = formLayoutService.rulesOf(settingsBean.getProject().getId(), settingsBean.getSelectedTable(),
                settingsBean.getSelectedTypeName(), target.getId());
        field = target;
        errorMessage = null;
        conceptChoices.clear();
        otherFields = settingsBean.getLayoutRows().stream()
                .filter(LayoutRow::isFieldRow)
                .map(LayoutRow::getField)
                .filter(f -> !f.getId().equals(target.getId()))
                .toList();
        enabledWhen = draftOf(rules.enabledWhen());
        requiredWhen = draftOf(rules.requiredWhen());
        advancedOptions = null;
        optionsFieldId = null;
        // The editor has no screen for the sources of a place field: it keeps them as they are.
        keptPlaceSources = rules.placeSources();
        if (rules.options() instanceof OptionsFilter.RelatedConcepts related) {
            optionsFieldId = related.fieldId();
        } else if (rules.options() != null) {
            advancedOptions = rules.options();
        }
        constraints = rules.constraints().stream().map(c -> {
            ConstraintDraft draft = new ConstraintDraft();
            draft.setOp(c.op());
            draft.setFieldId(c.fieldId());
            return draft;
        }).collect(Collectors.toCollection(ArrayList::new));
        open = true;
    }

    // ---- What the view needs ----

    public boolean isOwnConcept() {
        return field != null && field.getRuleFamily() == RuleFieldFamily.CONCEPT;
    }

    public boolean isOwnOrdered() {
        return field != null && field.getRuleFamily() != null && field.getRuleFamily().isOrdered();
    }

    public List<TypeFieldFormConfig> getConceptFields() {
        return otherFields.stream().filter(f -> f.getRuleFamily() == RuleFieldFamily.CONCEPT).toList();
    }

    /** The fields this one can be ordered against: those of the same family. */
    public List<TypeFieldFormConfig> getComparableFields() {
        return otherFields.stream()
                .filter(f -> field != null && f.getRuleFamily() == field.getRuleFamily())
                .toList();
    }

    public String labelOf(TypeFieldFormConfig f) {
        return settingsBean.resolveFieldLabel(f);
    }

    public RuleFieldFamily familyOf(Long fieldId) {
        return otherFields.stream().filter(f -> f.getId().equals(fieldId)).map(TypeFieldFormConfig::getRuleFamily)
                .findFirst().orElse(null);
    }

    /** The operators that make sense for the field a comparison reads. */
    public List<ConditionOp> operatorsFor(Long fieldId) {
        RuleFieldFamily family = familyOf(fieldId);
        if (family == null) return List.of();
        return switch (family) {
            case NUMBER, DATE -> List.of(ConditionOp.EQ, ConditionOp.NEQ, ConditionOp.GT, ConditionOp.GTE,
                    ConditionOp.LT, ConditionOp.LTE, ConditionOp.EMPTY, ConditionOp.NOT_EMPTY);
            case CONCEPT -> List.of(ConditionOp.IN, ConditionOp.NOT_IN, ConditionOp.EMPTY, ConditionOp.NOT_EMPTY);
            case TEXT -> List.of(ConditionOp.EQ, ConditionOp.NEQ, ConditionOp.EMPTY, ConditionOp.NOT_EMPTY);
            case OTHER -> List.of(ConditionOp.EMPTY, ConditionOp.NOT_EMPTY);
        };
    }

    public String opLabel(ConditionOp op) {
        return langBean.msg("rules.op." + op.name());
    }

    public String constraintOpLabel(FieldConstraint.Op op) {
        return langBean.msg("rules.constraint." + op.name());
    }

    public List<FieldConstraint.Op> getConstraintOps() {
        return List.of(FieldConstraint.Op.values());
    }

    public boolean needsValue(LeafDraft leaf) {
        return leaf.getOp() != ConditionOp.EMPTY && leaf.getOp() != ConditionOp.NOT_EMPTY;
    }

    /** The concepts a comparison on a concept field can be matched against. */
    public List<Choice> choicesFor(Long fieldId) {
        if (fieldId == null) return List.of();
        return conceptChoices.computeIfAbsent(fieldId, this::loadChoices);
    }

    private List<Choice> loadChoices(Long fieldId) {
        CustomField source = formLayoutService.fieldOf(settingsBean.getProject().getId(), settingsBean.getSelectedTable(),
                settingsBean.getSelectedTypeName(), fieldId).orElse(null);
        if (!(source instanceof CustomFieldConcept conceptField)) return List.of();
        try {
            Map<String, String> byId = new LinkedHashMap<>();
            for (ConceptAutocompleteDTO dto : fieldConfigurationService.fetchAutocomplete(conceptField, "",
                    settingsBean.getProject().getId())) {
                byId.putIfAbsent(String.valueOf(dto.getConceptLabelToDisplay().getConcept().getId()),
                        dto.getConceptLabelToDisplay().getLabel());
            }
            return byId.entrySet().stream().map(e -> new Choice(e.getKey(), e.getValue())).toList();
        } catch (NoConfigForFieldException e) {
            log.warn("No vocabulary configured for field {}", fieldId, e);
            return List.of();
        }
    }

    // ---- Editing ----

    public void addLeaf(ConditionDraft draft) {
        draft.getLeaves().add(new LeafDraft());
    }

    public void removeLeaf(ConditionDraft draft, LeafDraft leaf) {
        draft.getLeaves().remove(leaf);
    }

    public void clearAdvanced(ConditionDraft draft) {
        draft.setAdvanced(null);
    }

    /** Called when the field a comparison reads changes: the operator and value no longer apply. */
    public void onLeafFieldChanged(LeafDraft leaf) {
        List<ConditionOp> ops = operatorsFor(leaf.getFieldId());
        leaf.setOp(ops.isEmpty() ? ConditionOp.EQ : ops.get(0));
        leaf.setValues(new ArrayList<>());
        leaf.setValue(null);
    }

    public void addConstraint() {
        constraints.add(new ConstraintDraft());
    }

    public void removeConstraint(ConstraintDraft draft) {
        constraints.remove(draft);
    }

    public void clearAdvancedOptions() {
        advancedOptions = null;
    }

    public void save() {
        errorMessage = null;
        FieldRules rules;
        try {
            rules = new FieldRules(conditionOf(enabledWhen), conditionOf(requiredWhen), optionsOf(), constraintsOf(), keptPlaceSources);
        } catch (IllegalArgumentException e) {
            errorMessage = langBean.msg("rules.error.badValue");
            return;
        }
        try {
            formLayoutService.saveRules(settingsBean.getProject().getId(), settingsBean.getSelectedTable(),
                    settingsBean.getSelectedTypeName(), field.getId(), rules);
        } catch (InvalidRulesException e) {
            errorMessage = e.getIssues().stream()
                    .map(issue -> langBean.msg(issue.key(), issue.args().toArray()))
                    .collect(Collectors.joining(" "));
            return;
        }
        // the editor stays on the field: it is a section of the field's own screen now
        errorMessage = null;
        settingsBean.reloadLayoutRows();
    }

    // ---- Draft <-> rules ----

    private ConditionDraft draftOf(Condition condition) {
        ConditionDraft draft = new ConditionDraft();
        if (condition == null) return draft;
        if (condition instanceof Condition.Leaf leaf && representable(leaf)) {
            draft.getLeaves().add(leafDraftOf(leaf));
        } else if (condition instanceof Condition.All all && all.conditions().stream().allMatch(this::isSimpleLeaf)) {
            all.conditions().forEach(c -> draft.getLeaves().add(leafDraftOf((Condition.Leaf) c)));
        } else if (condition instanceof Condition.Any any && any.conditions().stream().allMatch(this::isSimpleLeaf)) {
            draft.setAny(true);
            any.conditions().forEach(c -> draft.getLeaves().add(leafDraftOf((Condition.Leaf) c)));
        } else {
            draft.setAdvanced(condition);
        }
        return draft;
    }

    private boolean isSimpleLeaf(Condition c) {
        return c instanceof Condition.Leaf leaf && representable(leaf);
    }

    private boolean representable(Condition.Leaf leaf) {
        return leaf.values().stream().noneMatch(FieldValueSpec.RefValue.class::isInstance);
    }

    private LeafDraft leafDraftOf(Condition.Leaf leaf) {
        LeafDraft draft = new LeafDraft();
        draft.setFieldId(leaf.fieldId());
        draft.setOp(leaf.op());
        List<String> texts = leaf.values().stream().map(v -> v instanceof FieldValueSpec.ConceptValue c
                ? String.valueOf(c.conceptId()) : String.valueOf(((FieldValueSpec.LiteralValue) v).value())).toList();
        if (familyOf(leaf.fieldId()) == RuleFieldFamily.CONCEPT) {
            // EQ / NEQ on a concept read the same as IN / NOT_IN with one value
            if (leaf.op() == ConditionOp.EQ) draft.setOp(ConditionOp.IN);
            if (leaf.op() == ConditionOp.NEQ) draft.setOp(ConditionOp.NOT_IN);
            draft.setValues(new ArrayList<>(texts));
        } else if (!texts.isEmpty()) {
            draft.setValue(texts.get(0));
        }
        return draft;
    }

    private Condition conditionOf(ConditionDraft draft) {
        if (draft.isAdvanced()) return draft.getAdvanced();
        List<Condition> leaves = draft.getLeaves().stream()
                .filter(l -> l.getFieldId() != null)
                .map(this::leafOf)
                .toList();
        if (leaves.isEmpty()) return null;
        if (leaves.size() == 1) return leaves.get(0);
        return draft.isAny() ? new Condition.Any(leaves) : new Condition.All(leaves);
    }

    private Condition leafOf(LeafDraft draft) {
        RuleFieldFamily family = familyOf(draft.getFieldId());
        List<FieldValueSpec> values = new ArrayList<>();
        if (needsValue(draft)) {
            if (family == RuleFieldFamily.CONCEPT) {
                draft.getValues().forEach(v -> values.add(FieldValueSpec.concept(Long.parseLong(v))));
            } else if (draft.getValue() != null && !draft.getValue().isBlank()) {
                String text = draft.getValue().trim();
                if (family == RuleFieldFamily.NUMBER) {
                    BigDecimal number = new BigDecimal(text.replace(',', '.'));
                    values.add(FieldValueSpec.literal(number.stripTrailingZeros().scale() <= 0
                            ? (Object) number.longValue() : (Object) number.doubleValue()));
                } else {
                    values.add(FieldValueSpec.literal(text));
                }
            }
        }
        return new Condition.Leaf(draft.getFieldId(), draft.getOp(), values);
    }

    private OptionsFilter optionsOf() {
        if (advancedOptions != null) return advancedOptions;
        return optionsFieldId == null ? null : new OptionsFilter.RelatedConcepts(optionsFieldId);
    }

    private List<FieldConstraint> constraintsOf() {
        return constraints.stream()
                .filter(c -> c.getFieldId() != null)
                .map(c -> new FieldConstraint(c.getOp(), c.getFieldId()))
                .toList();
    }
}
