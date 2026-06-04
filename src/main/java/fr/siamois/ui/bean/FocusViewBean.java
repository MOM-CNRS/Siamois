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

    private AbstractPanel resolvePanel(String path) {

        if (path.startsWith("/")) {
            path = path.substring(1);
        }

        String[] pathAndQuery = path.split("\\?", 2);

        String cleanPath = pathAndQuery[0];

        Map<String, String> queryParams = new HashMap<>();

        if (pathAndQuery.length > 1) {

            String query = pathAndQuery[1];

            for (String param : query.split("&")) {

                String[] kv = param.split("=", 2);

                if (kv.length == 2) {
                    queryParams.put(kv[0], kv[1]);
                }
            }
        }

        String[] parts = cleanPath.split("/");

        String type = parts[0];

        Long id = parts.length > 1
                ? Long.parseLong(parts[1])
                : null;

        Integer tabParam = queryParams.containsKey("tab")
                ? Integer.parseInt(queryParams.get("tab"))
                : null;

        Long viewId = queryParams.containsKey("viewId")
                ? Long.parseLong(queryParams.get("viewId"))
                : null;

        boolean isListPanel = id == null;

        AbstractPanel panel = switch (type) {

            case "recording-unit" ->
                    isListPanel
                            ? panelFactory.createRecordingUnitListPanel(viewId)
                            : panelFactory.createRecordingUnitPanel(id);

            case "action-unit" ->
                    isListPanel
                            ? panelFactory.createActionUnitListPanel(viewId)
                            : panelFactory.createActionUnitPanel(id);

            case "spatial-unit" ->
                    isListPanel
                            ? panelFactory.createSpatialUnitListPanel(viewId)
                            : panelFactory.createSpatialUnitPanel(id);

            case "specimen" ->
                    isListPanel
                            ? panelFactory.createSpecimenListPanel(viewId)
                            : panelFactory.createSpecimenPanel(id);

            case "container" ->
                    isListPanel
                            ? panelFactory.createContainerListPanel()
                            : panelFactory.createContainerPanel(id);

            case "phase" ->
                    isListPanel
                            ? panelFactory.createPhaseListPanel()
                            : panelFactory.createPhasePanel(id);

            case "welcome" ->
                    panelFactory.createWelcomePanel();

            default ->
                    throw new IllegalArgumentException(
                            "Unknown panel type: " + type
                    );
        };

        if (!isListPanel
                && tabParam != null
                && panel instanceof AbstractSingleEntityPanel singlePanel) {

            singlePanel.setActiveTabIndex(tabParam);
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
            main.setIcon(mainPanel.getIcon());
            if(mainPanel instanceof AbstractListPanel<?>) {
                main.setTitle(mainPanel.resolveTitleOrTitleCode());
            }
            else {
                main.setTitle(mainPanel.resolveTitleOrTitleCode());
            }

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

