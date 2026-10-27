package fr.siamois.domain.models.exporttemplate;

import lombok.Getter;

/** Un autre modèle de l'institution porte déjà la même paire nom + version. */
@Getter
public class DuplicateExportTemplateException extends RuntimeException {

    private final String templateName;
    private final String templateVersion;

    public DuplicateExportTemplateException(String templateName, String templateVersion) {
        super("An export template named '" + templateName + "' in version '" + templateVersion + "' already exists");
        this.templateName = templateName;
        this.templateVersion = templateVersion;
    }
}
