package fr.siamois.ui.bean;

import fr.siamois.domain.events.publisher.InstitutionChangeEventPublisher;
import fr.siamois.domain.events.publisher.LoginEventPublisher;
import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.services.PageTargetService;
import fr.siamois.domain.services.UiViewService;
import fr.siamois.domain.services.ResourceInstitutionService;
import fr.siamois.domain.services.permissions.ProfilePermissionService;
import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.dto.view.UITableViewDTO;
import fr.siamois.ui.bean.panel.PanelFactory;
import fr.siamois.ui.bean.panel.models.panel.AbstractPanel;
import jakarta.faces.context.FacesContext;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Named;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;

import java.io.Serializable;
import java.util.Base64;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Named
@ViewScoped
@Data
@RequiredArgsConstructor
public class FocusViewBean implements Serializable {

    private final transient PanelFactory panelFactory;
    private final transient HistoryBean historyBean;
    private final LangBean langBean;
    private final SessionSettingsBean sessionSettingsBean;
    private final transient ResourceInstitutionService resourceInstitutionService;
    private final transient ProfilePermissionService profilePermissionService;
    private final transient InstitutionChangeEventPublisher institutionChangeEventPublisher;
    private final transient LoginEventPublisher loginEventPublisher;
    private final transient RedirectBean redirectBean;
    private final transient UiViewService uiViewService;
    private final transient PageTargetService pageTargetService;

    private static final String FICHE_PANEL_KIND = "detail";
    private static final String HOME_PANEL_KIND = "home";


    private AbstractPanel mainPanel;

    private String mainPanelId;
    private String secondaryPanelId; // optionnel

    private String decodedMain;
    private String decodedSide;

    // tokens reçus depuis l'URL
    private String mainToken;
    private String secondaryToken;
    private String backToken;

    /**
     * A resource URI: its type, its entity id when it names one entity rather than a list, and the saved
     * view a list opens on ({@code ?viewId=}).
     */
    private record ParsedPath(String type, Long id, Long viewId) {
    }

    // Of the query string only ?viewId= is read, for a list (the former ?tab= is ignored: React keeps its
    // own tab and list state). A viewId that isn't a number is no view.
    private ParsedPath parsePath(String path) {
        if (path.startsWith("/")) path = path.substring(1);
        int query = path.indexOf('?');
        String[] parts = (query >= 0 ? path.substring(0, query) : path).split("/");
        String type = parts.length > 0 ? parts[0] : "";
        Long id = parts.length > 1 ? Long.valueOf(parts[1]) : null;
        return new ParsedPath(type, id, id == null && query >= 0 ? viewIdOf(path.substring(query + 1)) : null);
    }

    private static Long viewIdOf(String queryString) {
        for (String param : queryString.split("&")) {
            if (param.startsWith("viewId=")) {
                try {
                    return Long.valueOf(param.substring("viewId=".length()));
                } catch (NumberFormatException e) {
                    return null;
                }
            }
        }
        return null;
    }

    public void beforeInit() {
        if (mainToken != null) {
            ParsedPath parsedMain = parsePath(decodeToken(mainToken));

            if (!activateInstitutionOf(parsedMain)) {
                return;
            }

            mainPanel = panelFactory.create(parsedMain.type(), parsedMain.id());
            mainPanel.setRoot(true);
            applyView(mainPanel, parsedMain.viewId());
            if (backToken != null) {
                mainPanel.setGoBackUrl(decodeToken(backToken));
            }
        }

        if (secondaryToken != null && !secondaryToken.isEmpty()) {
            ParsedPath parsedSide = parsePath(decodeToken(secondaryToken));
            AbstractPanel overviewPanel = panelFactory.create(parsedSide.type(), parsedSide.id());
            overviewPanel.setRoot(false);
            mainPanel.setParentOrOverview(overviewPanel);
            overviewPanel.setParentOrOverview(mainPanel);
        }

        if (mainPanel != null) {
            String kind = mainPanel.reactPanelKind();
            historyBean.addLoadedPage(historyItemOf(mainPanel), FICHE_PANEL_KIND.equals(kind) || HOME_PANEL_KIND.equals(kind));
        }
    }

    /**
     * Keeps the view's main panel in step with a client-side navigation of the React main pane
     * (reactAction_setMain({path: "/action-unit/12", record: true})), which otherwise never reaches
     * the server: a later overview opened through the bridge is paired with the main pane actually on
     * screen, not the one the page loaded with. The history records the page only when {@code record}
     * says so (a fiche opened full screen, a table opened from the home page): scrolling through fiches
     * or going Back/Forward adds nothing.
     * The overview still open, if any, stays attached to the new main panel. A page deleted since (a
     * Back/Forward move can lead to one) is left to React, which shows it as such: the panel would
     * redirect the whole page to the 404.
     */
    public void setMainFromRequest() {
        Map<String, String> params = FacesContext.getCurrentInstance().getExternalContext().getRequestParameterMap();
        String path = params.get("path");
        if (path == null || path.isBlank() || mainPanel == null || pageTargetService.isMissing(path)) {
            return;
        }
        ParsedPath parsed;
        AbstractPanel next;
        try {
            parsed = parsePath(path);
            next = panelFactory.create(parsed.type(), parsed.id());
        } catch (IllegalArgumentException e) {
            log.warn("setMain : chemin React non reconnu côté serveur : {}", path);
            return;
        }
        if (!activateInstitutionOf(parsed)) {
            return;
        }

        AbstractPanel overview = mainPanel.getParentOrOverview();
        next.setRoot(true);
        applyView(next, parsed.viewId());
        if (overview != null) {
            next.setParentOrOverview(overview);
            overview.setParentOrOverview(next);
        }
        mainPanel = next;

        if (Boolean.parseBoolean(params.get("record"))) {
            historyBean.addItem(historyItemOf(next));
        }
    }

    /**
     * Whether this history entry is the page shown in the main pane of this view.
     */
    public boolean isCurrentPage(HistoryBean.HistoryItem item) {
        return mainPanel != null && item.getMain().getUri().equals(mainPanel.reactResourceUri());
    }

    private static HistoryBean.HistoryItem historyItemOf(AbstractPanel panel) {
        HistoryBean.HistoryItem item = new HistoryBean.HistoryItem();
        item.setMain(historyComponentOf(panel));
        return item;
    }

    /** Opens the panel on a saved view: the view's name is then the panel's title (history, favorite, tab). */
    private void applyView(AbstractPanel panel, Long viewId) {
        panel.setViewId(viewId);
        if (viewId == null) {
            return;
        }
        try {
            UITableViewDTO view = uiViewService.findOne(viewId);
            if (view != null && view.getTitle() != null && !view.getTitle().isBlank()) {
                panel.setTitleCodeOrTitle(view.getTitle());
            }
        } catch (IllegalArgumentException e) {
            log.warn("View {} not found, keeping the list's title", viewId);
        }
    }

    private static HistoryBean.HistoryItemComponent historyComponentOf(AbstractPanel panel) {
        HistoryBean.HistoryItemComponent component = new HistoryBean.HistoryItemComponent();
        component.setTitle(panel.resolveTitleOrTitleCode());
        component.setIcon(panel.getIcon());
        component.setUri(panel.reactResourceUri());
        component.setStyleClass(panel.getPanelClass());
        return component;
    }

    private boolean activateInstitutionOf(ParsedPath parsed) {
        InstitutionDTO target = resourceInstitutionService
                .findInstitutionOf(parsed.type(), parsed.id())
                .orElse(null);

        if (target == null) {
            // List and welcome panels belong to no entity: they are displayed in the active organisation.
            return true;
        }

        UserInfo userInfo = sessionSettingsBean.getUserInfo();
        PersonDTO user = userInfo == null ? null : userInfo.getUser();
        if (!profilePermissionService.canAccessInstitution(user, target)) {
            log.warn("Person {} tried to focus on {}/{} without access to organisation {}",
                    user, parsed.type(), parsed.id(), target.getId());
            redirectBean.redirectTo(HttpStatus.FORBIDDEN);
            return false;
        }

        InstitutionDTO active = sessionSettingsBean.getSelectedInstitution();
        if (active != null && Objects.equals(active.getId(), target.getId())) {
            return true;
        }

        sessionSettingsBean.setSelectedInstitution(target);
        institutionChangeEventPublisher.publishInstitutionChangeEvent();
        loginEventPublisher.publishLoginEvent();
        return true;
    }


    private String decodeToken(String token) {
        return new String(Base64.getUrlDecoder().decode(token));
    }

}
