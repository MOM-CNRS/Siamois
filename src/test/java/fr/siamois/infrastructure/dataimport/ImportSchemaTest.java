package fr.siamois.infrastructure.dataimport;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ImportSchemaTest {

    @Test
    void everyDefaultColumnLabelNamesAnExpectedColumnOfItsTable() {
        ImportSchema.COLUMN_LABELS.forEach((table, labels) ->
                assertThat(ImportSchema.EXPECTED_COLUMNS.get(table)).as(table).containsAll(labels.keySet()));
    }

    @Test
    void defaultHeadersAreUniquePerTable() {
        ImportSchema.COLUMN_LABELS.forEach((table, labels) -> {
            Set<String> seen = new HashSet<>();
            labels.values().forEach(label -> assertThat(seen.add(ExcelCellHelper.normalize(label)))
                    .as(table + " : " + label).isTrue());
        });
    }

    @Test
    void aColumnKeyHasTheSameHeaderInEveryTable() {
        Map<String, String> headerOfKey = new java.util.HashMap<>();
        ImportSchema.COLUMN_LABELS.values().forEach(labels -> labels.forEach((key, label) ->
                assertThat(headerOfKey.computeIfAbsent(key, k -> label)).as(key).isEqualTo(label)));
    }

    @Test
    void defaultColumnsHaveNoUri() {
        ImportSchema.COLUMN_LABELS.values().forEach(labels -> labels.keySet()
                .forEach(key -> assertThat(key).doesNotEndWith(" uri")));
    }

    @Test
    void tableIdOf_acceptsIdsNamesAndFormerNames() {
        assertThat(ImportSchema.tableIdOf("recording_unit ")).isEqualTo("recording_unit");
        assertThat(ImportSchema.tableIdOf("Unité d'enregistrement")).isEqualTo("recording_unit");
        assertThat(ImportSchema.tableIdOf("unite d'enregistrement")).isEqualTo("recording_unit");
        assertThat(ImportSchema.tableIdOf("UE")).isEqualTo("recording_unit");
        assertThat(ImportSchema.tableIdOf("Lieu")).isEqualTo("spatial_unit");
        assertThat(ImportSchema.tableIdOf("Relations entre lieux")).isEqualTo("spatialUnitRel");
        assertThat(ImportSchema.tableIdOf("Inconnue")).isEqualTo("Inconnue");
        assertThat(ImportSchema.tableIdOf(null)).isNull();
    }

    @Test
    void columnKey_and_columnLabel_goBothWays() {
        assertThat(ImportSchema.columnKey("recording_unit", "Interprétation normalisée")).isEqualTo("interpretation label");
        assertThat(ImportSchema.columnKey("recording_unit", "interpretation label")).isEqualTo("interpretation label");
        assertThat(ImportSchema.columnKey("recording_unit", "Autre colonne")).isEqualTo("autre colonne");
        assertThat(ImportSchema.columnLabel("interpretation label")).isEqualTo("Interprétation normalisée");
        assertThat(ImportSchema.columnLabel("type uri")).isEqualTo("type uri");
    }

    @Test
    void formerSheetNamesBelongToTablesWithADefaultName() {
        ImportSchema.LEGACY_SHEET_NAMES.forEach((table, names) -> {
            assertThat(ImportSchema.DEFAULT_SHEET_NAMES).containsKey(table);
            assertThat(names).isNotEmpty().doesNotContainAnyElementsOf(List.of(ImportSchema.DEFAULT_SHEET_NAMES.get(table)));
        });
    }
}
