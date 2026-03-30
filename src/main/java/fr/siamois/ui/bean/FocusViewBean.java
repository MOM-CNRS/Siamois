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
        // Remove leading '/' if present
        if (path.startsWith("/")) {
            path = path.substring(1);
        }

        String[] parts = path.split("/");
        String type = parts[0];

        // Séparer l'ID du paramètre (ex: "3?tab=2" -> id = "3", tab = "2")
        Long id = null;
        Integer tabParam = null;
        if (parts.length > 1) {
            String idWithParam = parts[1];
            if (idWithParam.contains("?")) {
                String[] idAndParam = idWithParam.split("\\?", 2);
                id = Long.parseLong(idAndParam[0]);
                // Extraire le paramètre "tab" si présent
                String[] params = idAndParam[1].split("&");
                for (String param : params) {
                    if (param.startsWith("tab=")) {
                        tabParam = Integer.parseInt(param.substring(4));
                        break;
                    }
                }
            } else {
                id = Long.parseLong(idWithParam);
            }
        }

        // Déterminer si c'est un panel de liste ou un panel unitaire
        boolean isListPanel = id == null;
// Créer le panel
        AbstractPanel panel = switch (type) {
            case "recording-unit" ->
                    isListPanel ? panelFactory.createRecordingUnitListPanel()
                            : panelFactory.createRecordingUnitPanel(id);
            case "action-unit" ->
                    isListPanel ? panelFactory.createActionUnitListPanel()
                            : panelFactory.createActionUnitPanel(id);
            case "spatial-unit" ->
                    isListPanel ? panelFactory.createSpatialUnitListPanel()
                            : panelFactory.createSpatialUnitPanel(id);
            case "specimen" ->
                    isListPanel ? panelFactory.createSpecimenListPanel()
                            : panelFactory.createSpecimenPanel(id);
            case "welcome" -> panelFactory.createWelcomePanel();
            default -> throw new IllegalArgumentException("Unknown panel type: " + type);
        };

        // Si c'est un panel unitaire et qu'un tab est spécifié, appliquer le paramètre
        if (!isListPanel && tabParam != null && panel instanceof AbstractSingleEntityPanel abstractSingleEntityPanel) {
            abstractSingleEntityPanel.setActiveTabIndex(tabParam);
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
                main.setTitle(langBean.msg(mainPanel.getTitleCodeOrTitle()));
            }
            else {
                main.setTitle(mainPanel.getTitleCodeOrTitle());
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
                side.setTitle(langBean.msg(overviewPanel.getTitleCodeOrTitle()));
            }
            else {
                side.setTitle(overviewPanel.getTitleCodeOrTitle());
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

