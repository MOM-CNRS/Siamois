package fr.siamois.ui.bean;

import fr.siamois.ui.bean.panel.PanelFactory;
import fr.siamois.ui.bean.panel.models.panel.AbstractPanel;
import fr.siamois.ui.bean.panel.models.panel.single.AbstractSingleEntity;
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
        if (!isListPanel && tabParam != null && panel instanceof AbstractSingleEntityPanel) {
            ((AbstractSingleEntityPanel) panel).setActiveTabIndex(tabParam);
        }

        return panel;
    }


    public void beforeInit() {

        if (mainToken != null) {
            String decoded = decodeToken(mainToken);
            mainPanel = resolvePanel(decoded);
            mainPanel.setRoot(true);
        }

        if (secondaryToken != null && !secondaryToken.isEmpty()) {
            String decoded = decodeToken(secondaryToken);
            AbstractPanel overviewPanel = resolvePanel(decoded);
            overviewPanel.setRoot(false);
            mainPanel.setParentOrOverview(overviewPanel);
            overviewPanel.setParentOrOverview(mainPanel);
        }
    }


    private String decodeToken(String token) {
        return new String(Base64.getUrlDecoder().decode(token));
    }

}

