package fr.siamois.ui.table.definitions;

import fr.siamois.domain.models.form.customfield.CustomField;
import fr.siamois.domain.models.settings.tableconfig.ConfigurableTable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import fr.siamois.utils.TestForms;

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
    @EnumSource(ConfigurableTable.class)
    void specsOf_shouldHoldTheSystemFieldsOfEveryConfigurableTable(ConfigurableTable table) {
        assertThat(SystemFieldCatalog.specsOf(table))
                .as("%s has no system field to configure", table)
                .isNotEmpty()
                .allSatisfy(spec -> assertThat(spec.field().getIsSystemField()).isTrue());
    }

    @ParameterizedTest
    @EnumSource(ConfigurableTable.class)
    void theInitialLayoutLaysOutEveryVisibleSystemFieldExactlyOnce(ConfigurableTable table) {
        List<Long> laidOut = TestForms.layoutOf(table).groups().stream()
                .flatMap(group -> group.items().stream())
                .map(item -> item.field().getId())
                .toList();
        List<Long> visible = SystemFieldCatalog.specsOf(table).stream()
                .filter(spec -> !spec.hidden())
                .map(spec -> spec.field().getId())
                .toList();

        assertThat(laidOut).doesNotHaveDuplicates().containsExactlyInAnyOrderElementsOf(visible);
    }

    /**
     * Compared by label rather than by field: two custom fields are equal by database id, which a
     * definition has none of.
     */
    @ParameterizedTest
    @EnumSource(ConfigurableTable.class)
    void fieldsOf_shouldHoldTheFieldsOfTheSpecs(ConfigurableTable table) {
        assertThat(SystemFieldCatalog.fieldsOf(table))
                .extracting(CustomField::getLabel)
                .containsExactlyElementsOf(SystemFieldCatalog.specsOf(table).stream()
                        .map(spec -> spec.field().getLabel())
                        .toList());
    }

    /**
     * {@link SystemFieldCatalog#identityOf} is what a definition and its row are matched by, so a
     * field missing either half of it would share an identity with every other field missing it —
     * they would all collapse onto a single row.
     */
    @ParameterizedTest
    @EnumSource(ConfigurableTable.class)
    void identityOf_shouldBeBuiltOnALabelAndABindingEveryDefinedFieldCarries(ConfigurableTable table) {
        assertThat(SystemFieldCatalog.fieldsOf(table)).allSatisfy(field -> {
            assertThat(field.getLabel()).as("label of a system field of %s", table).isNotBlank();
            assertThat(field.getValueBinding()).as("binding of %s", field.getLabel()).isNotBlank();
        });
    }

    @ParameterizedTest
    @EnumSource(ConfigurableTable.class)
    void identityOf_shouldTellTheFieldsOfATableApart(ConfigurableTable table) {
        assertThat(SystemFieldCatalog.fieldsOf(table))
                .extracting(SystemFieldCatalog::identityOf)
                .doesNotHaveDuplicates();
    }

    /**
     * The definitions carry ids of their own that are local to the class declaring them — the same
     * number stands for another field one definition over — so identity cannot be read from them.
     */
    @Test
    void identityOf_shouldNotDependOnTheIdsTheDefinitionsCarry() {
        Map<String, CustomField> fields = byLabel(ConfigurableTable.MOBILIER);
        CustomField category = fields.get("specimen.field.type");
        String identity = SystemFieldCatalog.identityOf(category);

        category.setId(4321L);

        assertThat(SystemFieldCatalog.identityOf(category)).isEqualTo(identity);
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
