package fr.siamois.infrastructure.dataimport;

import fr.siamois.infrastructure.database.initializer.seeder.ImportSpecs;

import java.util.List;

public record ImportResult(ImportSpecs specs, List<ImportError> errors, SheetMetadata meta) {
    public boolean hasErrors() { return !errors.isEmpty(); }
}
