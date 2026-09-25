package fr.siamois.ui.table.definitions;

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

    private static final Set<String> STRUCTURAL_COLUMN_IDS = Set.of("identifierCol", "relationships", "specimen");

    @Test
    void everyColumnIsAUniqueSystemFieldOfTheRecordingUnitForm() {
        List<String> columnIds = RecordingUnitTableColumnDefaults.columns().stream()
                .map(RecordingUnitTableColumnDefaults.ColumnDefault::columnId)
                .toList();

        assertThat(columnIds).doesNotHaveDuplicates().doesNotContainAnyElementsOf(STRUCTURAL_COLUMN_IDS);
        assertThat(RecordingUnitTableColumnDefaults.columns())
                .allSatisfy(column -> assertThat(SystemFieldCatalog.fieldsOf(ConfigurableTable.UE))
                        .extracting(field -> field.getId())
                        .contains(column.field().getId()));
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
