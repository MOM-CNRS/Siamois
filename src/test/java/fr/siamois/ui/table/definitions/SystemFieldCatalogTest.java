package fr.siamois.ui.table.definitions;

import fr.siamois.domain.models.form.customfield.CustomField;
import fr.siamois.domain.models.settings.tableconfig.ConfigurableTable;
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
 * The catalog is the contract between the definitions of the system fields and the two things that
 * read them: the initializer that gives each a row, and the configuration screen that lists them.
 */
class SystemFieldCatalogTest {

    @ParameterizedTest
    @EnumSource(ConfigurableTable.class)
    void columnsOf_shouldHoldTheSystemFieldsOfEveryConfigurableTable(ConfigurableTable table) {
        assertThat(SystemFieldCatalog.columnsOf(table))
                .as("%s has no system field to configure", table)
                .isNotEmpty()
                .allSatisfy(column -> {
                    Assertions.assertNotNull(column.getField());
                    assertThat(column.getField().getIsSystemField()).isTrue();
                });
    }

    @ParameterizedTest
    @EnumSource(ConfigurableTable.class)
    void columnsOf_shouldFollowTheOrderOfTheTableDefinition(ConfigurableTable table) {
        List<String> definitionOrder = definitionOf(table).getFieldColumns().stream()
                .filter(column -> {
                    Assertions.assertNotNull(column.getField());
                    return Boolean.TRUE.equals(column.getField().getIsSystemField());
                })
                .map(column -> column.getField().getLabel())
                .toList();

        assertThat(SystemFieldCatalog.columnsOf(table))
                .extracting(column -> {
                    Assertions.assertNotNull(column.getField());
                    return column.getField().getLabel();
                })
                .containsExactlyElementsOf(definitionOrder);
    }

    /**
     * Compared by label rather than by field: every call rebuilds the definition, and two custom
     * fields are equal by database id, which a definition has none of.
     */
    @ParameterizedTest
    @EnumSource(ConfigurableTable.class)
    void fieldsOf_shouldHoldTheFieldsOfTheColumns(ConfigurableTable table) {
        assertThat(SystemFieldCatalog.fieldsOf(table))
                .extracting(CustomField::getLabel)
                .containsExactlyElementsOf(SystemFieldCatalog.columnsOf(table).stream()
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
     * The identifier is declared by both the UE and the Mobilier definitions; it is the same field,
     * so both must resolve to the same row rather than to one apiece.
     */
    @Test
    void identityOf_shouldMatchAFieldDeclaredByTwoTables() {
        Map<String, CustomField> unitFields = byLabel(ConfigurableTable.UE);
        Map<String, CustomField> specimenFields = byLabel(ConfigurableTable.MOBILIER);
        String sharedLabel = "recordingunit.field.identifier";
        assertThat(unitFields).containsKey(sharedLabel);
        assertThat(specimenFields).containsKey(sharedLabel);

        assertThat(SystemFieldCatalog.identityOf(unitFields.get(sharedLabel)))
                .isEqualTo(SystemFieldCatalog.identityOf(specimenFields.get(sharedLabel)));
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

    @Test
    void columnsOf_shouldHandOutFreshFieldsSoACallerCannotAlterTheDefinition() {
        CustomField first = byLabel(ConfigurableTable.MOBILIER).get("specimen.field.category");
        first.setLabel("Modifié");

        assertThat(byLabel(ConfigurableTable.MOBILIER)).containsKey("specimen.field.category");
    }

    @Test
    void columnsOf_shouldRefuseToAnswerForNoTable() {
        assertThatThrownBy(() -> SystemFieldCatalog.columnsOf(null))
                .isInstanceOf(NullPointerException.class);
    }

    private Map<String, CustomField> byLabel(ConfigurableTable table) {
        return SystemFieldCatalog.fieldsOf(table).stream()
                .collect(Collectors.toMap(CustomField::getLabel, Function.identity(), (first, second) -> first));
    }

    private fr.siamois.ui.table.TableDefinition definitionOf(ConfigurableTable table) {
        return switch (table) {
            case UE -> RecordingUnitTableDefinitionFactory.definition();
            case MOBILIER -> SpecimenTableDefinitionFactory.definition();
            case PHASE -> PhaseTableDefinitionFactory.definition();
            case CONTENANT -> ContainerTableDefinitionFactory.definition();
        };
    }
}
