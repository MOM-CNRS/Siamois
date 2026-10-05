package fr.siamois.domain.services.exporttemplate;

import fr.siamois.domain.models.exporttemplate.ExportTemplateDefinition.Sheet;

import java.util.List;

/**
 * Feuille évaluée, prête à être écrite : chaque cellule est un {@link String} ou un {@link Double}.
 * {@code total} est le nombre de lignes lues dans les sources, avant limitation éventuelle (aperçu).
 */
record ExportSheetData(Sheet sheet, List<List<Object>> rows, int total) {
}
