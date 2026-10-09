package fr.siamois.domain.services.form.layout;

import fr.siamois.domain.models.form.config.FieldFormConfig;
import fr.siamois.domain.models.form.config.FieldWidth;
import fr.siamois.domain.models.form.config.FormConfig;
import fr.siamois.domain.models.form.config.FormConfigGroup;
import fr.siamois.domain.models.form.customfield.basetypes.CustomFieldText;
import fr.siamois.domain.models.form.layout.FormLayout;
import fr.siamois.domain.models.form.rules.ConditionOp;
import fr.siamois.domain.models.settings.tableconfig.ConfigurableTable;
import fr.siamois.domain.models.settings.tableconfig.LayoutRow;
import fr.siamois.domain.models.settings.tableconfig.TypeFieldFormConfig;
import fr.siamois.domain.models.settings.tableconfig.TypeSummary;
import fr.siamois.domain.services.settings.tableconfig.TableFieldConfigService;
import fr.siamois.infrastructure.database.repositories.form.config.FieldFormConfigRepository;
import fr.siamois.infrastructure.database.repositories.form.config.FormConfigGroupRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FormLayoutServiceTest {

    private static final ConfigurableTable TABLE = ConfigurableTable.UE;

    @Mock
    private TableFieldConfigService tableFieldConfigService;
    @Mock
    private FormConfigGroupRepository groupRepository;
    @Mock
    private FieldFormConfigRepository fieldFormConfigRepository;

    @Mock
    private fr.siamois.domain.services.form.rules.FieldRulesValidator rulesValidator;

    @InjectMocks
    private FormLayoutService service;

    @Test
    void aConfigurationWithoutGroupsHasNoLayout() {
        FormConfig config = config(1L);
        when(tableFieldConfigService.findFormConfig(5L, TABLE, 9L)).thenReturn(Optional.of(config));
        when(tableFieldConfigService.findFormConfig(5L, TABLE, (Long) null)).thenReturn(Optional.empty());
        when(groupRepository.findAllByFormConfigIdOrderByPosition(1L)).thenReturn(List.of());

        assertThat(service.storedLayout(5L, TABLE, 9L)).isEmpty();
    }

    @Test
    void theStoredLayoutKeepsGroupOrderFieldOrderWidthAndRules() {
        FormConfig config = config(1L);
        FormConfigGroup first = group(10L, config, "Identité", 0);
        FormConfigGroup second = group(11L, config, "Détails", 1);
        when(tableFieldConfigService.findFormConfig(5L, TABLE, 9L)).thenReturn(Optional.of(config));
        when(groupRepository.findAllByFormConfigIdOrderByPosition(1L)).thenReturn(List.of(first, second));
        when(fieldFormConfigRepository.findAllByFormConfigId(1L)).thenReturn(List.of(
                row(config, field(1L, "a"), second, FieldWidth.FULL, true, false, null),
                row(config, field(2L, "b"), first, FieldWidth.HALF, true, true,
                        "{\"enabledWhen\":{\"fieldId\":1,\"op\":\"EQ\",\"values\":[{\"conceptId\":\"77\"}]}}"),
                row(config, field(3L, "c"), first, FieldWidth.QUARTER, false, false, null)));

        FormLayout layout = service.storedLayout(5L, TABLE, 9L).orElseThrow();

        assertThat(layout.groups()).extracting(FormLayout.Group::label).containsExactly("Identité", "Détails");
        assertThat(layout.groups().get(0).items()).extracting(i -> i.field().getId()).containsExactly(2L, 3L);
        FormLayout.Item b = layout.groups().get(0).items().get(0);
        assertThat(b.width()).isEqualTo(FieldWidth.HALF);
        assertThat(b.mandatory()).isTrue();
        assertThat(b.rules().enabledWhen()).isNotNull();
        assertThat(layout.groups().get(0).items().get(1).active()).isFalse();
        assertThat(layout.groups().get(1).items().get(0).rules().isEmpty()).isTrue();
    }

    @Test
    void aTypeWithoutALayoutOfItsOwnUsesTheDefaultOneAsAWhole() {
        FormConfig own = config(1L);
        FormConfig defaults = config(2L);
        when(tableFieldConfigService.findFormConfig(5L, TABLE, 9L)).thenReturn(Optional.of(own));
        when(tableFieldConfigService.findFormConfig(5L, TABLE, (Long) null)).thenReturn(Optional.of(defaults));
        when(groupRepository.findAllByFormConfigIdOrderByPosition(1L)).thenReturn(List.of());
        FormConfigGroup group = group(20L, defaults, "Défaut", 0);
        when(groupRepository.findAllByFormConfigIdOrderByPosition(2L)).thenReturn(List.of(group));
        when(fieldFormConfigRepository.findAllByFormConfigId(2L))
                .thenReturn(List.of(row(defaults, field(1L, "a"), group, FieldWidth.QUARTER, true, false, null)));

        FormLayout layout = service.storedLayout(5L, TABLE, 9L).orElseThrow();

        assertThat(layout.groups()).extracting(FormLayout.Group::label).containsExactly("Défaut");
    }

    @Test
    void anUnreadableRuleIsIgnoredRatherThanBreakingTheForm() {
        FormConfig config = config(1L);
        FormConfigGroup group = group(10L, config, "G", 0);
        when(tableFieldConfigService.findFormConfig(5L, TABLE, 9L)).thenReturn(Optional.of(config));
        when(groupRepository.findAllByFormConfigIdOrderByPosition(1L)).thenReturn(List.of(group));
        when(fieldFormConfigRepository.findAllByFormConfigId(1L)).thenReturn(List.of(
                row(config, field(1L, "a"), group, FieldWidth.QUARTER, true, false, "{\"enabledWhen\":{\"nope\":1}}")));

        FormLayout layout = service.storedLayout(5L, TABLE, 9L).orElseThrow();

        assertThat(layout.groups().get(0).items().get(0).rules().isEmpty()).isTrue();
    }

    @Test
    void theDefaultConfigurationDoesNotFallBackOnItself() {
        when(tableFieldConfigService.findFormConfig(5L, TABLE, (Long) null)).thenReturn(Optional.empty());

        assertThat(service.storedLayout(5L, TABLE, null)).isEmpty();
        verify(tableFieldConfigService, never()).findFormConfig(5L, TABLE, 9L);
        assertThat(ConditionOp.valueOf("EQ")).isNotNull();
    }

    // ---- editing ----

    private FormConfig laidOutConfig() {
        FormConfig config = config(1L);
        when(tableFieldConfigService.listTypes(5L, TABLE)).thenReturn(List.of(new TypeSummary("Type")));
        when(tableFieldConfigService.findFormConfig(5L, TABLE, "Type")).thenReturn(Optional.of(config));
        when(groupRepository.countByFormConfigId(1L)).thenReturn(2L);
        org.mockito.Mockito.lenient().when(groupRepository.save(any(FormConfigGroup.class))).thenAnswer(call -> {
            FormConfigGroup g = call.getArgument(0);
            if (g.getId() == null) g.setId(99L);
            return g;
        });
        return config;
    }

    @Test
    void savingAnArrangementRegroupsReordersRenamesAndAddsGroups() {
        FormConfig config = laidOutConfig();
        FormConfigGroup a = group(10L, config, "A", 0);
        FormConfigGroup b = group(11L, config, "B", 1);
        FieldFormConfig fa = row(config, field(1L, "fa"), a, FieldWidth.QUARTER, true, false, null);
        FieldFormConfig fb = row(config, field(2L, "fb"), a, FieldWidth.QUARTER, true, false, null);
        FieldFormConfig fc = row(config, field(3L, "fc"), b, FieldWidth.QUARTER, true, false, null);
        when(groupRepository.findAllByFormConfigIdOrderByPosition(1L)).thenReturn(List.of(a, b));
        when(fieldFormConfigRepository.findAllByFormConfigId(1L)).thenReturn(List.of(fa, fb, fc));

        service.saveArrangement(5L, TABLE, "Type", List.of(
                new FormLayoutService.GroupSpec(11L, "B2", List.of("fc")),
                new FormLayoutService.GroupSpec(null, "Nouveau", List.of("fb")),
                new FormLayoutService.GroupSpec(10L, "A", List.of("fa"))));

        assertThat(b.getLabel()).isEqualTo("B2");
        assertThat(b.getPosition()).isZero();
        assertThat(a.getPosition()).isEqualTo(2);
        assertThat(fc.getGroup()).isSameAs(b);
        assertThat(fb.getGroup().getLabel()).isEqualTo("Nouveau");
        assertThat(fb.getGroup().getPosition()).isEqualTo(1);
        assertThat(fb.getPosition()).isEqualTo(1);
        assertThat(fa.getGroup()).isSameAs(a);
        verify(groupRepository, never()).delete(any());
    }

    @Test
    void aGroupLeftOutOfTheArrangementIsDeletedAndItsStrayFieldsGoToTheLastGroup() {
        FormConfig config = laidOutConfig();
        FormConfigGroup a = group(10L, config, "A", 0);
        FormConfigGroup b = group(11L, config, "B", 1);
        FieldFormConfig fa = row(config, field(1L, "fa"), a, FieldWidth.QUARTER, true, false, null);
        FieldFormConfig fc = row(config, field(3L, "fc"), b, FieldWidth.QUARTER, true, false, null);
        when(groupRepository.findAllByFormConfigIdOrderByPosition(1L)).thenReturn(List.of(a, b));
        when(fieldFormConfigRepository.findAllByFormConfigId(1L)).thenReturn(List.of(fa, fc));

        service.saveArrangement(5L, TABLE, "Type", List.of(new FormLayoutService.GroupSpec(10L, "A", List.of("fa"))));

        verify(groupRepository).delete(b);
        assertThat(fc.getGroup()).isSameAs(a);
        assertThat(fc.getPosition()).isEqualTo(2);
    }

    @Test
    void anArrangementNeedsAtLeastOneGroup() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.saveArrangement(5L, TABLE, "Type", List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void theWidthOfAFieldIsStoredOnItsRow() {
        FormConfig config = laidOutConfig();
        FieldFormConfig fa = row(config, field(1L, "fa"), group(10L, config, "A", 0), FieldWidth.QUARTER, true, false, null);
        when(fieldFormConfigRepository.findAllByFormConfigId(1L)).thenReturn(List.of(fa));

        service.setFieldWidth(5L, TABLE, "Type", "fa", FieldWidth.FULL);

        assertThat(fa.getWidth()).isEqualTo(FieldWidth.FULL);
        verify(fieldFormConfigRepository).save(fa);
    }

    @Test
    void aConfigurationWithoutLayoutGetsItsEffectiveFormWrittenAsGroups() {
        FormConfig config = config(1L);
        when(tableFieldConfigService.listTypes(5L, TABLE)).thenReturn(List.of(new TypeSummary("Type")));
        when(tableFieldConfigService.findFormConfig(5L, TABLE, "Type")).thenReturn(Optional.of(config));
        when(groupRepository.countByFormConfigId(1L)).thenReturn(0L);
        CustomFieldText a = field(1L, "fa");
        CustomFieldText b = field(2L, "fb");
        when(tableFieldConfigService.legacyLayout(5L, TABLE, "Type")).thenReturn(new FormLayout(List.of(
                new FormLayout.Group(null, "G1", List.of(
                        new FormLayout.Item(a, FieldWidth.HALF, true, true, false, fr.siamois.domain.models.form.rules.FieldRules.NONE))),
                new FormLayout.Group(null, "G2", List.of(
                        new FormLayout.Item(b, FieldWidth.QUARTER, false, false, false, fr.siamois.domain.models.form.rules.FieldRules.NONE))))));
        FieldFormConfig existing = row(config, b, null, FieldWidth.QUARTER, true, false, null);
        when(fieldFormConfigRepository.findAllByFormConfigId(1L)).thenReturn(List.of(existing));
        when(groupRepository.save(any(FormConfigGroup.class))).thenAnswer(call -> call.getArgument(0));

        service.ensureLayouts(5L, TABLE);

        ArgumentCaptor<FieldFormConfig> saved = ArgumentCaptor.forClass(FieldFormConfig.class);
        verify(fieldFormConfigRepository, atLeastOnce()).save(saved.capture());
        FieldFormConfig written = saved.getAllValues().stream().filter(r -> r.getField() == a).findFirst().orElseThrow();
        assertThat(written.getGroup().getLabel()).isEqualTo("G1");
        assertThat(written.getWidth()).isEqualTo(FieldWidth.HALF);
        assertThat(written.isMandatory()).isTrue();
        assertThat(existing.getGroup().getLabel()).isEqualTo("G2");
        assertThat(existing.isActive()).isFalse();
    }

    @Test
    void aConfigurationThatAlreadyHasGroupsIsLeftAlone() {
        laidOutConfig();

        service.ensureLayouts(5L, TABLE);

        verify(tableFieldConfigService, never()).legacyLayout(any(), any(), any());
        verify(groupRepository, never()).save(any());
    }

    @Test
    void theRowsOfAScreenListEachGroupHeaderBeforeItsFields() {
        FormConfig config = config(1L);
        FormConfigGroup group = group(10L, config, "G", 0);
        CustomFieldText a = field(1L, "fa");
        when(tableFieldConfigService.findFormConfig(5L, TABLE, "Type")).thenReturn(Optional.of(config));
        when(groupRepository.findAllByFormConfigIdOrderByPosition(1L)).thenReturn(List.of(group));
        when(fieldFormConfigRepository.findAllByFormConfigId(1L))
                .thenReturn(List.of(row(config, a, group, FieldWidth.THREE_QUARTERS, true, true, null)));
        TypeFieldFormConfig described = TypeFieldFormConfig.builder().name("fa").build();
        when(tableFieldConfigService.describe(a, true, true, false)).thenReturn(described);

        List<LayoutRow> rows = service.rowsOf(5L, TABLE, "Type");

        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).isGroup()).isTrue();
        assertThat(rows.get(0).getGroupId()).isEqualTo(10L);
        assertThat(rows.get(1).getField()).isSameAs(described);
        assertThat(rows.get(1).getWidth()).isEqualTo(FieldWidth.THREE_QUARTERS);
    }

    @Test
    void validRulesAreStoredAsJsonOnTheFieldRow() {
        FormConfig config = laidOutConfig();
        FieldFormConfig fa = row(config, field(1L, "fa"), group(10L, config, "A", 0), FieldWidth.QUARTER, true, false, null);
        when(fieldFormConfigRepository.findAllByFormConfigId(1L)).thenReturn(List.of(fa));
        when(rulesValidator.validate(any(), org.mockito.ArgumentMatchers.eq(1L), any())).thenReturn(List.of());

        service.saveRules(5L, TABLE, "Type", 1L, fr.siamois.domain.models.form.rules.FieldRules.NONE
                .withEnabledWhen(fr.siamois.domain.models.form.rules.Condition.notEmpty(2L)));

        assertThat(fa.getRules()).contains("enabledWhen");
        verify(fieldFormConfigRepository).save(fa);
    }

    @Test
    void emptyRulesClearTheStoredOnes() {
        FormConfig config = laidOutConfig();
        FieldFormConfig fa = row(config, field(1L, "fa"), group(10L, config, "A", 0), FieldWidth.QUARTER, true, false, "{\"a\":1}");
        when(fieldFormConfigRepository.findAllByFormConfigId(1L)).thenReturn(List.of(fa));
        when(rulesValidator.validate(any(), org.mockito.ArgumentMatchers.eq(1L), any())).thenReturn(List.of());

        service.saveRules(5L, TABLE, "Type", 1L, fr.siamois.domain.models.form.rules.FieldRules.NONE);

        assertThat(fa.getRules()).isNull();
    }

    @Test
    void invalidRulesAreRefusedAndNothingIsStored() {
        FormConfig config = laidOutConfig();
        FieldFormConfig fa = row(config, field(1L, "fa"), group(10L, config, "A", 0), FieldWidth.QUARTER, true, false, null);
        when(fieldFormConfigRepository.findAllByFormConfigId(1L)).thenReturn(List.of(fa));
        when(rulesValidator.validate(any(), org.mockito.ArgumentMatchers.eq(1L), any()))
                .thenReturn(List.of(new fr.siamois.domain.services.form.rules.FieldRulesValidator.Issue("rules.error.selfReference")));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.saveRules(5L, TABLE, "Type", 1L,
                        fr.siamois.domain.models.form.rules.FieldRules.NONE))
                .isInstanceOf(InvalidRulesException.class);
        assertThat(fa.getRules()).isNull();
        verify(fieldFormConfigRepository, never()).save(fa);
    }

    @Test
    void aFieldLockedByTheInstitutionKeepsItsRules() {
        FormConfig config = laidOutConfig();
        FieldFormConfig fa = row(config, field(1L, "fa"), group(10L, config, "A", 0), FieldWidth.QUARTER, true, false, null);
        fa.setInstitutionLocked(true);
        when(fieldFormConfigRepository.findAllByFormConfigId(1L)).thenReturn(List.of(fa));

        service.saveRules(5L, TABLE, "Type", 1L, fr.siamois.domain.models.form.rules.FieldRules.NONE
                .withEnabledWhen(fr.siamois.domain.models.form.rules.Condition.notEmpty(2L)));

        assertThat(fa.getRules()).isNull();
        verify(rulesValidator, never()).validate(any(), org.mockito.ArgumentMatchers.anyLong(), any());
    }

    private static FormConfig config(long id) {
        FormConfig config = new FormConfig();
        config.setId(id);
        return config;
    }

    private static FormConfigGroup group(long id, FormConfig config, String label, int position) {
        FormConfigGroup group = new FormConfigGroup(config, label, position);
        group.setId(id);
        return group;
    }

    private static CustomFieldText field(long id, String binding) {
        return CustomFieldText.builder().id(id).label(binding).valueBinding(binding).build();
    }

    private static FieldFormConfig row(FormConfig config, CustomFieldText field, FormConfigGroup group, FieldWidth width,
                                       boolean active, boolean mandatory, String rules) {
        FieldFormConfig ffc = new FieldFormConfig();
        ffc.setFormConfig(config);
        ffc.setField(field);
        ffc.setGroup(group);
        ffc.setWidth(width);
        ffc.setActive(active);
        ffc.setMandatory(mandatory);
        ffc.setRules(rules);
        return ffc;
    }
}
