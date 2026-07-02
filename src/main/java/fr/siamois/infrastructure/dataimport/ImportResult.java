package fr.siamois.infrastructure.dataimport;

import fr.siamois.infrastructure.database.initializer.seeder.ImportSpecs;

import java.util.List;
import java.util.Map;

/**
 * @param allSheetColumns  raw column headers per sheet (all except _meta), for display
 */
public record ImportResult(
        ImportSpecs specs,
        List<ImportError> errors,
        SheetMetadata meta,
        Map<String, List<String>> allSheetColumns
) {
    public boolean hasErrors() { return !errors.isEmpty(); }
}
