package fr.siamois.ui.table.definitions;

import fr.siamois.ui.table.TableDefinition;
import fr.siamois.ui.table.column.FormFieldColumn;
import fr.siamois.ui.table.column.TableColumn;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Keeps {@link RecordingUnitTableDefinitionFactory} (the JSF table) and
 * {@link RecordingUnitTableColumnDefaults} (the REST catalog's source of truth) from drifting
 * apart — the whole point of extracting the defaults out of the factory in the first place.
 */
class RecordingUnitTableColumnDefaultsTest {

    private static final Set<String> STRUCTURAL_COLUMN_IDS = Set.of("identifierCol", "relationships", "specimen");

    @Test
    void definitionUsesEveryDefaultsColumnExactlyOnce() {
        TableDefinition definition = RecordingUnitTableDefinitionFactory.definition();

        List<String> fieldColumnIds = definition.getColumns().stream()
                .filter(FormFieldColumn.class::isInstance)
                .map(TableColumn::getId)
                .toList();

        List<String> defaultsColumnIds = RecordingUnitTableColumnDefaults.columns().stream()
                .map(RecordingUnitTableColumnDefaults.ColumnDefault::columnId)
                .toList();

        assertThat(fieldColumnIds).containsExactlyInAnyOrderElementsOf(defaultsColumnIds);
    }

    @Test
    void definitionDoesNotDuplicateTheStructuralColumns() {
        TableDefinition definition = RecordingUnitTableDefinitionFactory.definition();

        List<String> defaultsColumnIds = RecordingUnitTableColumnDefaults.columns().stream()
                .map(RecordingUnitTableColumnDefaults.ColumnDefault::columnId)
                .toList();

        for (String structuralId : STRUCTURAL_COLUMN_IDS) {
            assertThat(defaultsColumnIds).doesNotContain(structuralId);
        }
        // identifierCol lives on its own dedicated slot (setCommandLinkColumn), not in the plain
        // columns list; relationships/specimen are RelationColumns, hand-built in the factory.
        assertThat(definition.getCommandLinkColumn().getId()).isEqualTo("identifierCol");
        assertThat(definition.findColumnById("relationships")).isPresent();
        assertThat(definition.findColumnById("specimen")).isPresent();
    }

    @Test
    void everyFieldColumnAgreesWithItsDefaultsEntryOnShapeAndVisibility() {
        TableDefinition definition = RecordingUnitTableDefinitionFactory.definition();

        for (RecordingUnitTableColumnDefaults.ColumnDefault expected : RecordingUnitTableColumnDefaults.columns()) {
            FormFieldColumn actual = (FormFieldColumn) definition.findColumnById(expected.columnId())
                    .orElseThrow(() -> new AssertionError("Missing column '" + expected.columnId() + "' in the factory's own definition"));

            assertThat(actual.getField()).as("field of '%s'", expected.columnId()).isEqualTo(expected.field());
            assertThat(actual.isVisible()).as("visible of '%s'", expected.columnId()).isEqualTo(expected.visible());
            assertThat(actual.isSortable()).as("sortable of '%s'", expected.columnId()).isEqualTo(expected.sortable());
            assertThat(actual.isFilterable()).as("filterable of '%s'", expected.columnId()).isEqualTo(expected.filterable());
            assertThat(actual.getSortField()).as("sortField of '%s'", expected.columnId()).isEqualTo(expected.sortField());
            assertThat(actual.isRequired()).as("required of '%s'", expected.columnId()).isEqualTo(expected.required());
            assertThat(actual.isReadOnly()).as("readOnly of '%s'", expected.columnId()).isEqualTo(expected.readOnly());
            // Every field-catalog column must be toggleable — the bug the extraction fixed (see
            // RecordingUnitTableColumnDefaults' own javadoc).
            assertThat(actual.isToggleable()).as("toggleable of '%s'", expected.columnId()).isTrue();
        }
    }

    @Test
    void defaultVisibleFieldIdsMatchesTheVisibleColumns() {
        Set<String> expected = RecordingUnitTableColumnDefaults.columns().stream()
                .filter(RecordingUnitTableColumnDefaults.ColumnDefault::visible)
                .map(RecordingUnitTableColumnDefaults.ColumnDefault::fieldId)
                .collect(java.util.stream.Collectors.toSet());

        assertThat(RecordingUnitTableColumnDefaults.defaultVisibleFieldIds()).isEqualTo(expected);
    }
}
