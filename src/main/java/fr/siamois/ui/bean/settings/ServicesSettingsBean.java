package fr.siamois.ui.bean.settings;

import fr.siamois.domain.services.permissions.ProfilePermissionService;
import fr.siamois.ui.bean.LangBean;
import fr.siamois.ui.bean.RedirectBean;
import fr.siamois.ui.bean.SessionSettingsBean;
import fr.siamois.ui.bean.settings.components.OptionElement;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.io.Serializable;
import java.util.List;

/**
 * Page « Services » des paramètres : liste des services de l'institution courante (export, import à venir).
 */
@Component
@Scope(value = "session", proxyMode = ScopedProxyMode.TARGET_CLASS)
@RequiredArgsConstructor
public class ServicesSettingsBean implements Serializable {

    private final transient ProfilePermissionService profilePermissionService;
    private final SessionSettingsBean sessionSettingsBean;
    private final LangBean langBean;
    private final RedirectBean redirectBean;

    public void checkAccessOrRedirect() {
        if (!profilePermissionService.canViewInstitutionData(
                sessionSettingsBean.getUserInfo().getUser(), sessionSettingsBean.getSelectedInstitution())) {
            redirectBean.redirectTo(HttpStatus.NOT_FOUND);
        }
    }

    public List<OptionElement> getElements() {
        OptionElement export = new OptionElement("bi bi-file-earmark-spreadsheet",
                langBean.msg("services.export.title"), langBean.msg("services.export.description"), () -> {
            redirectBean.redirectTo("/settings/export-templates");
            return null;
        });
        OptionElement importService = new OptionElement("bi bi-box-arrow-in-down",
                langBean.msg("services.import.title"), langBean.msg("services.import.description"), () -> null,
                true, langBean.msg("services.import.soon"));
        return List.of(export, importService);
    }
}
