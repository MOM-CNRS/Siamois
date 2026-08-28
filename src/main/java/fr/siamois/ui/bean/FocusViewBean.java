package fr.siamois.ui.bean;

import fr.siamois.domain.events.publisher.InstitutionChangeEventPublisher;
import fr.siamois.domain.events.publisher.LoginEventPublisher;
import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.services.ResourceInstitutionService;
import fr.siamois.domain.services.permissions.ProfilePermissionService;
import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.ui.bean.panel.PanelFactory;
import fr.siamois.ui.bean.panel.models.panel.AbstractPanel;
import fr.siamois.ui.bean.panel.models.panel.list.AbstractListPanel;
import fr.siamois.ui.bean.panel.models.panel.single.AbstractSingleEntityPanel;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Named;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;

import java.io.Serializable;
import java.util.Base64;
import java.util.HashMap;
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


    private AbstractPanel mainPanel;

    private String mainPanelId;
    private String secondaryPanelId; // optionnel

    private String decodedMain;
    private String decodedSide;

    // tokens reçus depuis l'URL
    private String mainToken;
    private String secondaryToken;
    private String backToken;

    private record ParsedPath(String type, Long id, Integer tab, Long viewId) {
        boolean isListPanel() { return id == null; }
    }

    private Map<String, String> parseQueryParams(String query) {
        Map<String, String> params = new HashMap<>();
        if (query.isBlank()) return params;
        for (String param : query.split("&")) {
            String[] kv = param.split("=", 2);
            if (kv.length == 2) params.put(kv[0], kv[1]);
        }
        return params;
    }

    private ParsedPath parsePath(String path) {
        if (path.startsWith("/")) path = path.substring(1);
        String[] pathAndQuery = path.split("\\?", 2);
        String[] parts = pathAndQuery[0].split("/");
        Map<String, String> q = parseQueryParams(pathAndQuery.length > 1 ? pathAndQuery[1] : "");
        return new ParsedPath(
                parts[0],
                parts.length > 1 ? Long.parseLong(parts[1]) : null,
                q.containsKey("tab")    ? Integer.parseInt(q.get("tab"))    : null,
                q.containsKey("viewId") ? Long.parseLong(q.get("viewId"))   : null
        );
    }

    private AbstractPanel createPanel(ParsedPath p) {
        return switch (p.type()) {
            case "recording-unit" -> p.isListPanel() ? panelFactory.createRecordingUnitListPanel(p.viewId()) : panelFactory.createRecordingUnitPanel(p.id());
            case "action-unit"    -> p.isListPanel() ? panelFactory.createActionUnitListPanel(p.viewId())    : panelFactory.createActionUnitPanel(p.id());
            case "spatial-unit"   -> p.isListPanel() ? panelFactory.createSpatialUnitListPanel(p.viewId())   : panelFactory.createSpatialUnitPanel(p.id());
            case "specimen"       -> p.isListPanel() ? panelFactory.createSpecimenListPanel(p.viewId())      : panelFactory.createSpecimenPanel(p.id());
            case "container"      -> p.isListPanel() ? panelFactory.createContainerListPanel()               : panelFactory.createContainerPanel(p.id());
            case "phase"          -> p.isListPanel() ? panelFactory.createPhaseListPanel()                   : panelFactory.createPhasePanel(p.id());
            case "welcome"        -> panelFactory.createWelcomePanel();
            default               -> throw new IllegalArgumentException("Unknown panel type: " + p.type());
        };
    }

    private AbstractPanel resolvePanel(ParsedPath parsed) {
        AbstractPanel panel = createPanel(parsed);
        if (!parsed.isListPanel() && parsed.tab() != null && panel instanceof AbstractSingleEntityPanel<?> sp) {
            sp.setActiveTabIndex(parsed.tab());
        }
        return panel;
    }


    public void beforeInit() {
        HistoryBean.HistoryItem newEntry = new HistoryBean.HistoryItem();

        if (mainToken != null) {
            ParsedPath parsedMain = parsePath(decodeToken(mainToken));

            if (!activateInstitutionOf(parsedMain)) {
                return;
            }

            HistoryBean.HistoryItemComponent main = new HistoryBean.HistoryItemComponent();
            mainPanel = resolvePanel(parsedMain);
            mainPanel.setRoot(true);
            if (backToken != null) {
                mainPanel.setGoBackUrl(decodeToken(backToken));
            }
            main.setIcon(mainPanel.getIcon());
            main.setTitle(mainPanel.resolveTitleOrTitleCode());
            main.setUri(mainPanel.ressourceUri());
            main.setStyleClass(mainPanel.getPanelClass());
            newEntry.setMain(main);
        }

        if (secondaryToken != null && !secondaryToken.isEmpty()) {
            HistoryBean.HistoryItemComponent side = new HistoryBean.HistoryItemComponent();

            AbstractPanel overviewPanel = resolvePanel(parsePath(decodeToken(secondaryToken)));
            overviewPanel.setRoot(false);
            mainPanel.setParentOrOverview(overviewPanel);
            overviewPanel.setParentOrOverview(mainPanel);
            side.setIcon(overviewPanel.getIcon());
            if(overviewPanel instanceof AbstractListPanel<?>) {
                side.setTitle(langBean.msg(overviewPanel.resolveTitleOrTitleCode()));
            }
            else {
                side.setTitle(overviewPanel.resolveTitleOrTitleCode());
            }

            side.setUri(overviewPanel.ressourceUri());
            side.setStyleClass(overviewPanel.getPanelClass());
            newEntry.setSecondary(side);
        }

        historyBean.addItem(newEntry);

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
        historyBean.getItems().clear(); // entries of the previous organisation are no longer reachable
        institutionChangeEventPublisher.publishInstitutionChangeEvent();
        loginEventPublisher.publishLoginEvent();
        return true;
    }


    private String decodeToken(String token) {
        return new String(Base64.getUrlDecoder().decode(token));
    }

}
