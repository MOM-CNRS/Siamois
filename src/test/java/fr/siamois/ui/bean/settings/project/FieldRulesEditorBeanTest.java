package fr.siamois.ui.bean.settings.project;

import fr.siamois.domain.models.form.rules.Condition;
import fr.siamois.domain.models.form.rules.ConditionOp;
import fr.siamois.domain.models.form.rules.FieldConstraint;
import fr.siamois.domain.models.form.rules.FieldRules;
import fr.siamois.domain.models.form.rules.FieldValueSpec;
import fr.siamois.domain.models.form.rules.OptionsFilter;
import fr.siamois.domain.models.form.rules.RuleFieldFamily;
import fr.siamois.domain.models.settings.tableconfig.ConfigurableTable;
import fr.siamois.domain.models.settings.tableconfig.LayoutRow;
import fr.siamois.domain.models.settings.tableconfig.TypeFieldFormConfig;
import fr.siamois.domain.services.form.layout.FormLayoutService;
import fr.siamois.domain.services.form.layout.InvalidRulesException;
import fr.siamois.domain.services.form.rules.FieldRulesValidator;
import fr.siamois.domain.services.vocabulary.FieldConfigurationService;
import fr.siamois.dto.entity.ActionUnitDTO;
import fr.siamois.ui.bean.LangBean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FieldRulesEditorBeanTest {

    private FormLayoutService layout;
    private FieldRulesEditorBean bean;
    private final TypeFieldFormConfig own = field(1L, "own", RuleFieldFamily.CONCEPT);
    private final TypeFieldFormConfig nature = field(2L, "nature", RuleFieldFamily.CONCEPT);
    private final TypeFieldFormConfig depth = field(3L, "depth", RuleFieldFamily.NUMBER);
    private final TypeFieldFormConfig name = field(4L, "name", RuleFieldFamily.TEXT);

    private static TypeFieldFormConfig field(long id, String name, RuleFieldFamily family) {
        return TypeFieldFormConfig.builder().id(id).name(name).ruleFamily(family).build();
    }

    @BeforeEach
    void setUp() {
        layout = mock(FormLayoutService.class);
        ProjectTableFieldSettingsBean settings = mock(ProjectTableFieldSettingsBean.class);
        ActionUnitDTO project = new ActionUnitDTO();
        project.setId(42L);
        when(settings.getProject()).thenReturn(project);
        when(settings.getSelectedTable()).thenReturn(ConfigurableTable.UE);
        when(settings.getSelectedTypeName()).thenReturn("Creusement");
        when(settings.getLayoutRows()).thenReturn(List.of(LayoutRow.header(1L, "G"), LayoutRow.of(own, null),
                LayoutRow.of(nature, null), LayoutRow.of(depth, null), LayoutRow.of(name, null)));
        LangBean lang = mock(LangBean.class);
        when(lang.msg(org.mockito.ArgumentMatchers.anyString(), any(Object[].class)))
                .thenAnswer(call -> call.getArgument(0));
        when(lang.msg(org.mockito.ArgumentMatchers.anyString())).thenAnswer(call -> call.getArgument(0));
        bean = new FieldRulesEditorBean(layout, mock(FieldConfigurationService.class), settings, lang);
    }

    private FieldRules saved() {
        ArgumentCaptor<FieldRules> captor = ArgumentCaptor.forClass(FieldRules.class);
        verify(layout).saveRules(eq(42L), eq(ConfigurableTable.UE), eq("Creusement"), eq(1L), captor.capture());
        return captor.getValue();
    }

    @Test
    void storedRulesRoundTripThroughTheEditor() {
        FieldRules rules = new FieldRules(
                Condition.any(Condition.in(2L, FieldValueSpec.concept(77)),
                        new Condition.Leaf(3L, ConditionOp.GT, List.of(FieldValueSpec.literal(3L)))),
                Condition.notEmpty(4L),
                new OptionsFilter.RelatedConcepts(2L),
                List.of());
        when(layout.rulesOf(42L, ConfigurableTable.UE, "Creusement", 1L)).thenReturn(rules);

        bean.openFor(own);
        assertThat(bean.getEnabledWhen().isAny()).isTrue();
        assertThat(bean.getEnabledWhen().getLeaves()).hasSize(2);
        assertThat(bean.getEnabledWhen().getLeaves().get(0).getValues()).containsExactly("77");
        assertThat(bean.getOptionsFieldId()).isEqualTo(2L);
        bean.save();

        assertThat(saved()).isEqualTo(rules);
    }

    @Test
    void editingBuildsConditionsFromTheDrafts() {
        when(layout.rulesOf(anyLong(), any(), any(), anyLong())).thenReturn(FieldRules.NONE);
        bean.openFor(own);

        bean.addLeaf(bean.getRequiredWhen());
        FieldRulesEditorBean.LeafDraft leaf = bean.getRequiredWhen().getLeaves().get(0);
        leaf.setFieldId(3L);
        bean.onLeafFieldChanged(leaf);
        leaf.setOp(ConditionOp.GTE);
        leaf.setValue("2,5");
        bean.addConstraint();
        bean.getConstraints().get(0).setFieldId(3L);
        bean.getConstraints().get(0).setOp(FieldConstraint.Op.LTE);
        bean.save();

        FieldRules rules = saved();
        assertThat(rules.requiredWhen()).isEqualTo(new Condition.Leaf(3L, ConditionOp.GTE, List.of(FieldValueSpec.literal(2.5))));
        assertThat(rules.constraints()).containsExactly(new FieldConstraint(FieldConstraint.Op.LTE, 3L));
        assertThat(rules.enabledWhen()).isNull();
        assertThat(bean.isOpen()).isFalse();
    }

    @Test
    void anAdvancedRuleIsKeptUntilRemoved() {
        Condition advanced = Condition.not(Condition.notEmpty(4L));
        when(layout.rulesOf(42L, ConfigurableTable.UE, "Creusement", 1L))
                .thenReturn(FieldRules.NONE.withEnabledWhen(advanced));
        bean.openFor(own);
        assertThat(bean.getEnabledWhen().isAdvanced()).isTrue();

        bean.save();
        assertThat(saved().enabledWhen()).isEqualTo(advanced);
    }

    @Test
    void theOperatorsOfAConceptFieldExcludeOrdering() {
        when(layout.rulesOf(anyLong(), any(), any(), anyLong())).thenReturn(FieldRules.NONE);
        bean.openFor(own);

        assertThat(bean.operatorsFor(2L)).contains(ConditionOp.IN).doesNotContain(ConditionOp.GT);
        assertThat(bean.operatorsFor(3L)).contains(ConditionOp.GT);
        assertThat(bean.getConceptFields()).containsExactly(nature);
    }

    @Test
    void validationIssuesAreShownAndTheDrawerStaysOpen() {
        when(layout.rulesOf(anyLong(), any(), any(), anyLong())).thenReturn(FieldRules.NONE);
        doThrow(new InvalidRulesException(List.of(new FieldRulesValidator.Issue("rules.error.selfReference"))))
                .when(layout).saveRules(anyLong(), any(), any(), anyLong(), any());
        bean.openFor(own);

        bean.save();

        assertThat(bean.getErrorMessage()).isEqualTo("rules.error.selfReference");
        assertThat(bean.isOpen()).isTrue();
    }

    @Test
    void aNumberThatIsNotOneIsReportedInsteadOfCrashing() {
        when(layout.rulesOf(anyLong(), any(), any(), anyLong())).thenReturn(FieldRules.NONE);
        bean.openFor(own);
        bean.addLeaf(bean.getEnabledWhen());
        FieldRulesEditorBean.LeafDraft leaf = bean.getEnabledWhen().getLeaves().get(0);
        leaf.setFieldId(3L);
        leaf.setOp(ConditionOp.EQ);
        leaf.setValue("abc");

        bean.save();

        assertThat(bean.getErrorMessage()).isEqualTo("rules.error.badValue");
    }
}
