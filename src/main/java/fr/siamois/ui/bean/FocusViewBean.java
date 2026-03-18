package fr.siamois.ui.bean;

import fr.siamois.ui.bean.panel.PanelFactory;
import fr.siamois.ui.bean.panel.models.panel.AbstractPanel;
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
        Long id = parts.length > 1 ? Long.parseLong(parts[1]) : null;

        // Determine if it's a list or unit panel
        boolean isListPanel = id == null;

        return switch (type) {
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

