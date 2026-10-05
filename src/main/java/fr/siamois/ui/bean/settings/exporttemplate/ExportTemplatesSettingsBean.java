package fr.siamois.ui.bean.settings.exporttemplate;

import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.models.events.LoginEvent;
import fr.siamois.domain.models.exceptions.permission.ForbiddenOperationException;
import fr.siamois.domain.models.exporttemplate.InvalidExportTemplateException;
import fr.siamois.domain.models.permissions.PermissionConstants;
import fr.siamois.domain.services.exporttemplate.ExportTemplateService;
import fr.siamois.domain.services.permissions.ProfilePermissionService;
import fr.siamois.dto.entity.ExportTemplateDTO;
import fr.siamois.ui.bean.LangBean;
import fr.siamois.ui.bean.RedirectBean;
import fr.siamois.ui.bean.SessionSettingsBean;
import fr.siamois.utils.MessageUtils;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.primefaces.PrimeFaces;
import org.primefaces.model.DefaultStreamedContent;
import org.primefaces.model.StreamedContent;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;

/**
 * Écrans des paramètres « Modèles d'export » : liste des modèles de l'institution courante, création,
 * import / export du JSON, copie, suppression, et fiche d'un modèle.
 */
@Slf4j
@Getter
@Setter
@Component
@Scope(value = "session", proxyMode = ScopedProxyMode.TARGET_CLASS)
@RequiredArgsConstructor
public class ExportTemplatesSettingsBean implements Serializable {

    private static final String ERROR_FORBIDDEN = "common.error.forbidden";

    private static final String CREATE_DIALOG = "exportTemplateCreateDialog";
    private static final String IMPORT_DIALOG = "exportTemplateImportDialog";
    private static final String DELETE_DIALOG = "exportTemplateDeleteDialog";
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy").withZone(ZoneId.systemDefault());

    private final transient ExportTemplateService exportTemplateService;
    private final transient ProfilePermissionService profilePermissionService;
    private final SessionSettingsBean sessionSettingsBean;
    private final LangBean langBean;
    private final RedirectBean redirectBean;

    private List<ExportTemplateDTO> templates = new ArrayList<>();
    private List<ExportTemplateDTO> filteredTemplates = new ArrayList<>();
    private String filterText;

    /** Saisies des dialogues. */
    private String newTemplateName;
    private String importedJson;

    /** Modèle visé par la suppression en attente de confirmation. */
    private ExportTemplateDTO templateToDelete;

    // ------------------------------------------------------------------ accès

    private UserInfo userInfo() {
        return sessionSettingsBean.getUserInfo();
    }

    /** Garde de page : 404 si l'utilisateur ne peut pas voir les données de l'institution. */
    public void checkAccessOrRedirect() {
        if (!profilePermissionService.canViewInstitutionData(
                userInfo().getUser(), sessionSettingsBean.getSelectedInstitution())) {
            redirectBean.redirectTo(HttpStatus.NOT_FOUND);
        }
    }

    public boolean canManage() {
        return profilePermissionService.hasInstancePermission(
                userInfo().getUser(), PermissionConstants.INSTANCE_MANAGE_SETTINGS)
                || profilePermissionService.hasOrganizationPermission(
                userInfo(), PermissionConstants.ORGANIZATION_MANAGE_SETTINGS);
    }

    // ------------------------------------------------------------------ liste

    public void init() {
        filterText = null;
        templates = new ArrayList<>(exportTemplateService.findAll(userInfo()));
        onFilterType();
    }

    public void onFilterType() {
        String needle = normalize(filterText);
        filteredTemplates = templates.stream()
                .filter(t -> needle.isEmpty() || normalize(t.name()).contains(needle))
                .toList();
    }

    public String getTemplateToDeleteName() {
        return templateToDelete == null ? "" : templateToDelete.name();
    }

    public String formatDate(Instant instant) {
        return instant == null ? "" : DATE_FORMAT.format(instant);
    }

    private static String normalize(String s) {
        if (s == null) return "";
        return Normalizer.normalize(s, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .trim();
    }

    // ------------------------------------------------------------------ création / import

    public void displayCreateDialog() {
        newTemplateName = null;
        showDialog(CREATE_DIALOG);
    }

    public void create() {
        if (blank(newTemplateName)) {
            MessageUtils.displayErrorMessage(langBean, "exportTemplates.error.nameRequired");
            return;
        }
        try {
            exportTemplateService.createBlank(userInfo(), newTemplateName.trim(),
                    langBean.msg("exportTemplates.blank.sheetName"), langBean.msg("exportTemplates.blank.columnHeader"));
        } catch (ForbiddenOperationException e) {
            MessageUtils.displayErrorMessage(langBean, ERROR_FORBIDDEN);
            return;
        }
        hideDialog(CREATE_DIALOG);
        MessageUtils.displayInfoMessage(langBean, "exportTemplates.created", newTemplateName.trim());
        init();
    }

    public void displayImportDialog() {
        importedJson = null;
        showDialog(IMPORT_DIALOG);
    }

    public void importJson() {
        if (blank(importedJson)) {
            MessageUtils.displayErrorMessage(langBean, "exportTemplates.error.jsonRequired");
            return;
        }
        try {
            ExportTemplateDTO imported = exportTemplateService.importJson(userInfo(), importedJson);
            MessageUtils.displayInfoMessage(langBean, "exportTemplates.imported", imported.name());
        } catch (InvalidExportTemplateException e) {
            MessageUtils.displayErrorMessage(langBean, "exportTemplates.error.invalidJson", e.getMessage());
            return;
        } catch (ForbiddenOperationException e) {
            MessageUtils.displayErrorMessage(langBean, ERROR_FORBIDDEN);
            return;
        }
        hideDialog(IMPORT_DIALOG);
        init();
    }

    /** Ajoute à l'institution le modèle national livré avec l'application (référentiel du rapport d'opération). */
    public void addNationalTemplate() {
        try {
            ExportTemplateDTO added = exportTemplateService.createFromBuiltIn(userInfo(),
                    ExportTemplateService.BuiltInTemplate.NATIONAL_REPORT);
            MessageUtils.displayInfoMessage(langBean, "exportTemplates.imported", added.name());
        } catch (ForbiddenOperationException e) {
            MessageUtils.displayErrorMessage(langBean, ERROR_FORBIDDEN);
            return;
        }
        init();
    }

    // ------------------------------------------------------------------ actions de ligne

    public void duplicate(ExportTemplateDTO template) {
        try {
            exportTemplateService.duplicate(userInfo(), template.id(),
                    langBean.msg("exportTemplates.copyName", template.name()));
            MessageUtils.displayInfoMessage(langBean, "exportTemplates.duplicated", template.name());
        } catch (ForbiddenOperationException e) {
            MessageUtils.displayErrorMessage(langBean, ERROR_FORBIDDEN);
            return;
        } catch (NoSuchElementException e) {
            MessageUtils.displayErrorMessage(langBean, "exportTemplates.error.notFound");
        }
        init();
    }

    public void askDelete(ExportTemplateDTO template) {
        templateToDelete = template;
        showDialog(DELETE_DIALOG);
    }

    public void confirmDelete() {
        if (templateToDelete == null) {
            return;
        }
        try {
            exportTemplateService.delete(userInfo(), templateToDelete.id());
            MessageUtils.displayInfoMessage(langBean, "exportTemplates.deleted", templateToDelete.name());
        } catch (ForbiddenOperationException e) {
            MessageUtils.displayErrorMessage(langBean, ERROR_FORBIDDEN);
        } catch (NoSuchElementException e) {
            MessageUtils.displayErrorMessage(langBean, "exportTemplates.error.notFound");
        }
        templateToDelete = null;
        hideDialog(DELETE_DIALOG);
        init();
    }

    /** Téléchargement du JSON partageable d'un modèle. */
    public StreamedContent downloadJson(ExportTemplateDTO template) {
        String json = exportTemplateService.exportJson(userInfo(), template.id());
        return DefaultStreamedContent.builder()
                .name(fileName(template.name()) + ".json")
                .contentType("application/json")
                .stream(() -> new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)))
                .build();
    }

    private static String fileName(String name) {
        String ascii = normalize(name).replaceAll("[^a-z0-9]+", "-").replaceAll("(?:^-)|(?:-$)", "");
        return ascii.isEmpty() ? "export-template" : ascii;
    }

    // ------------------------------------------------------------------ utilitaires

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }

    private static void showDialog(String widgetVar) {
        PrimeFaces.current().ajax().update(widgetVar);
        PrimeFaces.current().executeScript("PF('" + widgetVar + "').show();");
    }

    private static void hideDialog(String widgetVar) {
        PrimeFaces.current().executeScript("PF('" + widgetVar + "').hide();");
    }

    @EventListener(LoginEvent.class)
    public void reset() {
        templates = new ArrayList<>();
        filteredTemplates = new ArrayList<>();
        filterText = null;
        newTemplateName = null;
        importedJson = null;
        templateToDelete = null;
    }
}
