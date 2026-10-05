package fr.siamois.ui.bean.settings.exporttemplate;

import fr.siamois.domain.services.exporttemplate.ExportWarning;
import fr.siamois.ui.bean.LangBean;

/** Message lisible d'un {@link ExportWarning} (clé {@code exportTemplates.run.warning.<code>}). */
final class ExportWarningMessages {

    private ExportWarningMessages() {
        throw new UnsupportedOperationException();
    }

    static String of(LangBean langBean, ExportWarning warning) {
        return langBean.msg("exportTemplates.run.warning." + warning.code().name(),
                nullToEmpty(warning.sheet()), nullToEmpty(warning.column()), nullToEmpty(warning.detail()));
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
