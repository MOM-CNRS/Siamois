package fr.siamois.ui.table.definitions;

import fr.siamois.domain.models.form.customfield.CustomField;
import fr.siamois.domain.models.settings.tableconfig.ConfigurableTable;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link RecordingUnitTableColumnDefaults} is the source of the recording-unit list's default columns
 * ({@code _default.tableColumns} of the recording-unit-types catalogs).
 */
class RecordingUnitTableColumnDefaultsTest {

    // The identifier chip is built by hand; the relation columns (relationships, specimen) are fields now.
    private static final Set<String> STRUCTURAL_COLUMN_IDS = Set.of("identifierCol");

    @Test
    void everyColumnIsAUniqueSystemFieldOfTheRecordingUnitForm() {
        List<String> columnIds = RecordingUnitTableColumnDefaults.columns().stream()
                .map(RecordingUnitTableColumnDefaults.ColumnDefault::columnId)
                .toList();

        assertThat(columnIds).doesNotHaveDuplicates().doesNotContainAnyElementsOf(STRUCTURAL_COLUMN_IDS);
        assertThat(RecordingUnitTableColumnDefaults.columns())
                .allSatisfy(column -> assertThat(SystemFieldCatalog.fieldsOf(ConfigurableTable.UE))
                        .extracting(CustomField::getId)
                        .contains(column.field().getId()));
    }

    @Test
    void theRelationColumnsAreFieldsOfTheirOwn() {
        assertThat(RecordingUnitTableColumnDefaults.columns())
                .filteredOn(column -> Set.of("isPartOf", "contains", "relationships", "specimen").contains(column.columnId()))
                .extracting(column -> column.field().getValueBinding())
                .containsExactly("parents", "children", "stratigraphicRelationships", "specimenList");
    }

    // The project and the type come first; the relations right after them.
    @Test
    void theRelationColumnsComeRightAfterTheProjectAndTheType() {
        assertThat(RecordingUnitTableColumnDefaults.columns())
                .extracting(RecordingUnitTableColumnDefaults.ColumnDefault::columnId)
                .startsWith("action", "type", "isPartOf", "contains", "relationships", "specimen");
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
