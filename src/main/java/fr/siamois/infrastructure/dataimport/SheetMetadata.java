package fr.siamois.infrastructure.dataimport;

import java.util.List;
import java.util.Map;

public record SheetMetadata(
        Map<String, List<String>> tableToSheets,
        Map<String, Map<String, String>> columnAliases
) {
    static SheetMetadata empty() {
        return new SheetMetadata(Map.of(), Map.of());
    }
}
