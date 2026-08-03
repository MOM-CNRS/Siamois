package fr.siamois.ui.table.definitions;

import fr.siamois.domain.models.container.Container;
import fr.siamois.domain.models.form.customfield.CustomField;
import fr.siamois.domain.models.phase.Phase;
import fr.siamois.domain.models.recordingunit.RecordingUnit;
import fr.siamois.domain.models.settings.tableconfig.ConfigurableTable;
import fr.siamois.domain.models.specimen.Specimen;
import fr.siamois.ui.form.dto.CustomColUiDto;
import fr.siamois.ui.form.dto.FormUiDto;
import org.junit.jupiter.api.Assertions;
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
    @EnumSource(ConfigurableTable.class)
    void systemColumnsOf_shouldHoldTheSystemFieldsOfEveryConfigurableTable(ConfigurableTable table) {
        assertThat(SystemFieldCatalog.systemColumnsOf(table))
                .as("%s has no system field to configure", table)
                .isNotEmpty()
                .allSatisfy(column -> {
                    Assertions.assertNotNull(column.getField());
                    assertThat(column.getField().getIsSystemField()).isTrue();
                });
    }

    @ParameterizedTest
    @EnumSource(ConfigurableTable.class)
    void systemColumnsOf_shouldFollowTheOrderOfTheDetailsForm(ConfigurableTable table) {
        List<String> formOrder = detailsFormOf(table).getLayout().stream()
                .flatMap(panel -> panel.getRows().stream())
                .flatMap(row -> row.getColumns().stream())
                .filter(column -> {
                    Assertions.assertNotNull(column.getField());
                    return Boolean.TRUE.equals(column.getField().getIsSystemField());
                })
                .map(column -> column.getField().getLabel())
                .toList();

        assertThat(SystemFieldCatalog.systemColumnsOf(table))
                .extracting(column -> {
                    Assertions.assertNotNull(column.getField());
                    return column.getField().getLabel();
                })
                .containsExactlyElementsOf(formOrder);
    }

    /**
     * Compared by label rather than by field: two custom fields are equal by database id, which a
     * definition has none of.
     */
    @ParameterizedTest
    @EnumSource(ConfigurableTable.class)
    void fieldsOf_shouldHoldTheFieldsOfTheColumns(ConfigurableTable table) {
        assertThat(SystemFieldCatalog.fieldsOf(table))
                .extracting(CustomField::getLabel)
                .containsExactlyElementsOf(SystemFieldCatalog.systemColumnsOf(table).stream()
                        .map(column -> {
                            Assertions.assertNotNull(column.getField());
                            return column.getField().getLabel();
                        })
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
        CustomField category = fields.get("specimen.field.category");
        String identity = SystemFieldCatalog.identityOf(category);

        category.setId(4321L);

        assertThat(SystemFieldCatalog.identityOf(category)).isEqualTo(identity);
    }

    /**
     * The details form is a shared, static singleton (e.g. {@link RecordingUnit#DETAILS_FORM}) —
     * unlike the old table-factory-backed catalog, which rebuilt a fresh definition on every call,
     * so the catalog must hand out independent copies of its fields rather than the form's own
     * instances, or a caller mutating one (tests routinely stamp an id on a field to simulate a
     * persisted row) would corrupt the singleton for the rest of the JVM's lifetime.
     */
    @Test
    void fieldsOf_shouldHandOutFreshFieldsSoACallerCannotAlterTheDefinition() {
        CustomField first = byLabel(ConfigurableTable.MOBILIER).get("specimen.field.category");
        first.setLabel("Modifié");

        assertThat(byLabel(ConfigurableTable.MOBILIER)).containsKey("specimen.field.category");
    }

    @Test
    void systemColumnsOf_shouldRefuseToAnswerForNoTable() {
        assertThatThrownBy(() -> SystemFieldCatalog.systemColumnsOf(null))
                .isInstanceOf(NullPointerException.class);
    }

    private Map<String, CustomField> byLabel(ConfigurableTable table) {
        return SystemFieldCatalog.fieldsOf(table).stream()
                .collect(Collectors.toMap(CustomField::getLabel, Function.identity(), (first, second) -> first));
    }

    private FormUiDto detailsFormOf(ConfigurableTable table) {
        return switch (table) {
            case UE -> RecordingUnit.DETAILS_FORM;
            case MOBILIER -> Specimen.DETAILS_FORM;
            case PHASE -> Phase.DETAILS_FORM;
            case CONTENANT -> Container.DETAILS_FORM;
        };
    }
}
