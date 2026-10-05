package fr.siamois.ui.bean.settings.exporttemplate;

import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.models.events.LoginEvent;
import fr.siamois.domain.models.exceptions.permission.ForbiddenOperationException;
import fr.siamois.domain.models.exporttemplate.InvalidExportTemplateException;
import fr.siamois.domain.services.exporttemplate.ExportEngine;
import fr.siamois.domain.services.exporttemplate.ExportService;
import fr.siamois.domain.services.exporttemplate.ExportTemplateService;
import fr.siamois.domain.services.permissions.ProfilePermissionService;
import fr.siamois.ui.bean.LangBean;
import fr.siamois.ui.bean.RedirectBean;
import fr.siamois.ui.bean.SessionSettingsBean;
import fr.siamois.utils.MessageUtils;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.primefaces.model.DefaultStreamedContent;
import org.primefaces.model.StreamedContent;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * Écran « Lancer un export » : choix d'un projet et d'un modèle de l'institution, production du classeur,
 * affichage du nombre de lignes par feuille et des avertissements, téléchargement du fichier. Le contrôle
 * des droits et du modèle est celui d'{@link ExportService}.
 */
@Slf4j
@Getter
@Setter
@Component
@Scope(value = "session", proxyMode = ScopedProxyMode.TARGET_CLASS)
@RequiredArgsConstructor
public class ExportRunBean implements Serializable {

    private static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    /** Option d'une liste déroulante : libellé affiché et valeur stockée. */
    public record Item(String label, String value) implements Serializable {
    }

    /** Une feuille du fichier produit et son nombre de lignes de données. */
    public record SheetLine(String name, int rows) implements Serializable {
    }

    private final transient ExportService exportService;
    private final transient ExportTemplateService exportTemplateService;
    private final transient ProfilePermissionService profilePermissionService;
    private final SessionSettingsBean sessionSettingsBean;
    private final LangBean langBean;
    private final RedirectBean redirectBean;

    private List<Item> templateItems = new ArrayList<>();
    private List<Item> projectItems = new ArrayList<>();
    private String templateId;
    private String projectId;

    @Setter(AccessLevel.NONE)
    private byte[] content;
    @Setter(AccessLevel.NONE)
    private String fileName;
    private List<SheetLine> sheetLines = new ArrayList<>();
    private List<String> warningMessages = new ArrayList<>();

    private UserInfo userInfo() {
        return sessionSettingsBean.getUserInfo();
    }

    /** Ouvre l'écran, avec éventuellement un modèle déjà choisi ; retourne la navigation vers la page. */
    public String open(Long preselectedTemplateId) {
        clearResult();
        projectId = null;
        templateId = preselectedTemplateId == null ? null : String.valueOf(preselectedTemplateId);
        loadTemplates();
        loadProjects();
        return "/pages/settings/exportRun.xhtml?faces-redirect=true";
    }

    /** Garde de page : 404 sans accès aux données de l'institution. */
    public void checkAccessOrRedirect() {
        if (!profilePermissionService.canViewInstitutionData(userInfo().getUser(), sessionSettingsBean.getSelectedInstitution())) {
            redirectBean.redirectTo(HttpStatus.NOT_FOUND);
        }
    }

    public void backToList() {
        redirectBean.redirectTo("/settings/export-templates");
    }

    private void loadTemplates() {
        templateItems = exportTemplateService.findAll(userInfo()).stream()
                .map(t -> new Item(t.name() + " (v" + t.version() + ")", String.valueOf(t.id())))
                .toList();
    }

    private void loadProjects() {
        projectItems = exportService.listReadableProjects(userInfo()).stream()
                .map(p -> new Item(projectLabel(p), String.valueOf(p.id())))
                .toList();
    }

    private static String projectLabel(ExportService.ProjectChoice p) {
        String id = p.fullIdentifier();
        return id == null || id.isBlank() ? p.name() : id + " — " + p.name();
    }

    public boolean isReady() {
        return templateId != null && !templateId.isBlank() && projectId != null && !projectId.isBlank();
    }

    public boolean isDone() {
        return content != null;
    }

    public int getTotalRows() {
        return sheetLines.stream().mapToInt(SheetLine::rows).sum();
    }

    /** Le choix change : le fichier produit n'est plus celui de la sélection. */
    public void onSelectionChange() {
        clearResult();
    }

    public void run() {
        clearResult();
        if (!isReady()) {
            MessageUtils.displayErrorMessage(langBean, "exportTemplates.run.error.selection");
            return;
        }
        try {
            ExportEngine.Result result = exportService.export(userInfo(), Long.valueOf(templateId), Long.valueOf(projectId));
            content = result.content();
            fileName = result.fileName();
            sheetLines = result.rowsBySheet().entrySet().stream().map(e -> new SheetLine(e.getKey(), e.getValue())).toList();
            warningMessages = result.warnings().stream().map(w -> ExportWarningMessages.of(langBean, w)).toList();
        } catch (ForbiddenOperationException e) {
            MessageUtils.displayErrorMessage(langBean, "common.error.forbidden");
        } catch (NoSuchElementException e) {
            MessageUtils.displayErrorMessage(langBean, "exportTemplates.error.notFound");
        } catch (InvalidExportTemplateException e) {
            MessageUtils.displayErrorMessage(langBean, "exportTemplates.run.error.template", e.getMessage());
        } catch (RuntimeException e) {
            log.error("Export of template {} on project {} failed", templateId, projectId, e);
            MessageUtils.displayErrorMessage(langBean, "exportTemplates.run.error.unexpected");
        }
    }

    /** Fichier produit par le dernier export. */
    public StreamedContent getFile() {
        if (content == null) {
            return null;
        }
        byte[] bytes = content;
        return DefaultStreamedContent.builder()
                .name(fileName)
                .contentType(XLSX)
                .stream(() -> new ByteArrayInputStream(bytes))
                .build();
    }

    private void clearResult() {
        content = null;
        fileName = null;
        sheetLines = new ArrayList<>();
        warningMessages = new ArrayList<>();
    }

    @EventListener(LoginEvent.class)
    public void reset() {
        clearResult();
        templateItems = new ArrayList<>();
        projectItems = new ArrayList<>();
        templateId = null;
        projectId = null;
    }
}
