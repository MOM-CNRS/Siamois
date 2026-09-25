package fr.siamois.ui.bean.panel;


import fr.siamois.ui.bean.panel.models.panel.AbstractPanel;
import fr.siamois.ui.bean.panel.models.panel.WelcomePanel;
import fr.siamois.ui.bean.panel.models.panel.list.*;
import fr.siamois.ui.bean.panel.models.panel.single.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * Creates the panel descriptors {@code focus.xhtml} mounts the React main panel from, by the type of
 * their resource URI ({@code /action-unit}, {@code /action-unit/12}, …).
 */
@Component
@RequiredArgsConstructor
public class PanelFactory {

    private final ObjectProvider<SpatialUnitListPanel> spatialUnitListPanelProvider;
    private final ObjectProvider<ActionUnitListPanel> actionUnitListPanelProvider;
    private final ObjectProvider<ContainerListPanel> containerListPanelProvider;
    private final ObjectProvider<RecordingUnitListPanel> recordingUnitListPanelProvider;
    private final ObjectProvider<SpecimenListPanel> specimenListPanelProvider;
    private final ObjectProvider<PhaseListPanel> phaseListPanelProvider;
    private final ObjectProvider<SpatialUnitPanel> spatialUnitPanelProvider;
    private final ObjectProvider<ActionUnitPanel> actionUnitPanelProvider;
    private final ObjectProvider<RecordingUnitPanel> recordingUnitPanelProvider;
    private final ObjectProvider<SpecimenPanel> specimenPanelProvider;
    private final ObjectProvider<ContainerPanel> containerPanelProvider;
    private final ObjectProvider<PhasePanel> phasePanelProvider;
    private final ObjectProvider<WelcomePanel> welcomePanelProvider;

    /**
     * The panel of a resource URI's first segment: its list without an id, one entity with one.
     *
     * @throws IllegalArgumentException for an unknown type
     */
    public AbstractPanel create(String type, Long id) {
        return switch (type) {
            case "recording-unit" -> id == null ? recordingUnitListPanelProvider.getObject() : entity(recordingUnitPanelProvider, id);
            case "action-unit" -> id == null ? actionUnitListPanelProvider.getObject() : entity(actionUnitPanelProvider, id);
            case "spatial-unit" -> id == null ? spatialUnitListPanelProvider.getObject() : entity(spatialUnitPanelProvider, id);
            case "specimen" -> id == null ? specimenListPanelProvider.getObject() : entity(specimenPanelProvider, id);
            case "container" -> id == null ? containerListPanelProvider.getObject() : entity(containerPanelProvider, id);
            case "phase" -> id == null ? phaseListPanelProvider.getObject() : entity(phasePanelProvider, id);
            case "welcome" -> welcomePanelProvider.getObject();
            default -> throw new IllegalArgumentException("Unknown panel type: " + type);
        };
    }

    /**
     * The panel of one entity, by the React registry key of its type ("project", "recordingUnit", …),
     * or null for an unknown key.
     */
    public AbstractEntityPanel<?> createForReactEntityType(String entityType, Long id) {
        return switch (entityType) {
            case "project" -> entity(actionUnitPanelProvider, id);
            case "recordingUnit" -> entity(recordingUnitPanelProvider, id);
            case "find" -> entity(specimenPanelProvider, id);
            case "phase" -> entity(phasePanelProvider, id);
            case "container" -> entity(containerPanelProvider, id);
            case "place" -> entity(spatialUnitPanelProvider, id);
            default -> null;
        };
    }

    public WelcomePanel createWelcomePanel() {
        return welcomePanelProvider.getObject();
    }

    private static <P extends AbstractEntityPanel<?>> P entity(ObjectProvider<P> provider, Long id) {
        P panel = provider.getObject();
        panel.setUnitId(id);
        panel.init();
        return panel;
    }
}
