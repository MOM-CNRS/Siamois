package fr.siamois.ui.table.definitions;

import fr.siamois.domain.models.form.customfield.CustomField;
import fr.siamois.domain.models.settings.tableconfig.ConfigurableTable;
import fr.siamois.utils.TestForms;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The catalog is the contract between the definitions of the system fields — read from each
 * entity's real details form — and the two things that consume them: the initializer that gives
 * each a row, and the field configuration screen that lists them.
 */
class SystemFieldCatalogTest {

    @ParameterizedTest
    @EnumSource(value = ConfigurableTable.class, mode = EnumSource.Mode.EXCLUDE, names = "PROJECT")
    void specsOf_shouldHoldTheSystemFieldsOfEveryConfigurableTable(ConfigurableTable table) {
        assertThat(SystemFieldCatalog.specsOf(table))
                .as("%s has no system field to configure", table)
                .isNotEmpty()
                .allSatisfy(spec -> assertThat(spec.field().getIsSystemField()).isTrue());
    }

    /** System fields the initial layouts leave out on purpose: a new project does not offer them. */
    private static final java.util.Set<String> LEFT_OUT_OF_THE_LAYOUT = java.util.Set.of("geomorphologicalCycle", "geomorphologicalAgent");

    @ParameterizedTest
    @EnumSource(value = ConfigurableTable.class, mode = EnumSource.Mode.EXCLUDE, names = "PROJECT")
    void theInitialLayoutLaysOutEveryVisibleSystemFieldExactlyOnce(ConfigurableTable table) {
        List<Long> laidOut = TestForms.layoutOf(table).groups().stream()
                .flatMap(group -> group.items().stream())
                .map(item -> item.field().getId())
                .toList();
        List<Long> visible = SystemFieldCatalog.specsOf(table).stream()
                .filter(spec -> !spec.hidden())
                .filter(spec -> !LEFT_OUT_OF_THE_LAYOUT.contains(spec.field().getValueBinding()))
                .map(spec -> spec.field().getId())
                .toList();

        assertThat(laidOut).doesNotHaveDuplicates().containsExactlyInAnyOrderElementsOf(visible);
    }

    /**
     * Compared by label rather than by field: two custom fields are equal by database id, which a
     * definition has none of.
     */
    @ParameterizedTest
    @EnumSource(value = ConfigurableTable.class, mode = EnumSource.Mode.EXCLUDE, names = "PROJECT")
    void fieldsOf_shouldHoldTheFieldsOfTheSpecs(ConfigurableTable table) {
        assertThat(SystemFieldCatalog.fieldsOf(table))
                .extracting(CustomField::getLabel)
                .containsExactlyElementsOf(SystemFieldCatalog.specsOf(table).stream()
                        .map(spec -> spec.field().getLabel())
                        .toList());
    }

    /**
     * A definition and its row are matched by id (persisted as-is, see {@code SystemFieldInitializer}), so every
     * system field carries one.
     */
    @ParameterizedTest
    @EnumSource(value = ConfigurableTable.class, mode = EnumSource.Mode.EXCLUDE, names = "PROJECT")
    void everySystemFieldCarriesAnId(ConfigurableTable table) {
        assertThat(SystemFieldCatalog.fieldsOf(table))
                .allSatisfy(field -> assertThat(field.getId()).as("id of %s", field.getLabel()).isNotNull());
    }

    @ParameterizedTest
    @EnumSource(value = ConfigurableTable.class, mode = EnumSource.Mode.EXCLUDE, names = "PROJECT")
    void theIdsTellTheFieldsOfATableApart(ConfigurableTable table) {
        assertThat(SystemFieldCatalog.fieldsOf(table))
                .extracting(CustomField::getId)
                .doesNotHaveDuplicates();
    }

    /**
     * Across tables, one id never names two fields: the unit's and the find's geometry share their label and
     * binding, and only their ids (-332, -438) tell a find form which one it holds.
     */
    @Test
    void anIdNamesOneFieldAcrossEveryTable() {
        Map<Long, String> fieldById = new java.util.HashMap<>();
        for (ConfigurableTable table : ConfigurableTable.values()) {
            for (CustomField field : SystemFieldCatalog.sharedFieldsOf(table)) {
                String what = field.getClass().getSimpleName() + " " + field.getLabel() + " " + field.getValueBinding();
                String other = fieldById.putIfAbsent(field.getId(), what);
                assertThat(other).as("field %s of %s", field.getId(), table).isIn(null, what);
            }
        }
        assertThat(fieldById.get(-332L)).isNotEqualTo(null);
        assertThat(fieldById.get(-438L)).isNotEqualTo(null);
    }

    /**
     * The declared fields are shared static definitions, so the catalog must hand out independent
     * copies of them, or a caller mutating one (tests routinely stamp an id on a field to simulate a
     * persisted row) would corrupt the definition for the rest of the JVM's lifetime.
     */
    @Test
    void fieldsOf_shouldHandOutFreshFieldsSoACallerCannotAlterTheDefinition() {
        CustomField first = byLabel(ConfigurableTable.MOBILIER).get("specimen.field.type");
        first.setLabel("Modifié");

        assertThat(byLabel(ConfigurableTable.MOBILIER)).containsKey("specimen.field.type");
    }

    @Test
    void specsOf_shouldRefuseToAnswerForNoTable() {
        assertThatThrownBy(() -> SystemFieldCatalog.specsOf(null))
                .isInstanceOf(NullPointerException.class);
    }

    private Map<String, CustomField> byLabel(ConfigurableTable table) {
        return SystemFieldCatalog.fieldsOf(table).stream()
                .collect(Collectors.toMap(CustomField::getLabel, Function.identity(), (first, second) -> first));
    }
}
