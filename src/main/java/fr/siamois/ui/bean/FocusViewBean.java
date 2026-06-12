package fr.siamois.ui.bean;

import fr.siamois.ui.bean.panel.PanelFactory;
import fr.siamois.ui.bean.panel.models.panel.AbstractPanel;
import fr.siamois.ui.bean.panel.models.panel.list.AbstractListPanel;
import fr.siamois.ui.bean.panel.models.panel.single.AbstractSingleEntityPanel;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Named;
import lombok.Data;
import lombok.RequiredArgsConstructor;

import java.io.Serializable;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

@Named
@ViewScoped
@Data
@RequiredArgsConstructor
public class FocusViewBean implements Serializable {

    private final transient PanelFactory panelFactory;
    private final transient HistoryBean historyBean;
    private final LangBean langBean;


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

    private AbstractPanel resolvePanel(String path) {
        ParsedPath parsed = parsePath(path);
        AbstractPanel panel = createPanel(parsed);
        if (!parsed.isListPanel() && parsed.tab() != null && panel instanceof AbstractSingleEntityPanel<?> sp) {
            sp.setActiveTabIndex(parsed.tab());
        }
        return panel;
    }


    public void beforeInit() {
        HistoryBean.HistoryItem newEntry = new HistoryBean.HistoryItem();

        if (mainToken != null) {
            HistoryBean.HistoryItemComponent main = new HistoryBean.HistoryItemComponent();
            String decoded = decodeToken(mainToken);
            mainPanel = resolvePanel(decoded);
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

            String decoded = decodeToken(secondaryToken);
            AbstractPanel overviewPanel = resolvePanel(decoded);
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


    private String decodeToken(String token) {
        return new String(Base64.getUrlDecoder().decode(token));
    }

}

