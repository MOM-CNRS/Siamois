package fr.siamois.domain.models.exporttemplate;

/** Le JSON d'un modèle d'export ne respecte pas la grammaire de {@link ExportTemplateJson}. */
public class InvalidExportTemplateException extends IllegalArgumentException {

    public InvalidExportTemplateException(String message) {
        super(message);
    }

    public InvalidExportTemplateException(String message, Throwable cause) {
        super(message, cause);
    }
}
