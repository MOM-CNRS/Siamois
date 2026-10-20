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

    // Keys that are both a resource URI segment and a React registry key.
    private static final String CONTAINER = "container";
    private static final String PHASE = "phase";
    private static final String DOCUMENT = "document";
    private static final String STUDY = "study";

    private final ObjectProvider<SpatialUnitListPanel> spatialUnitListPanelProvider;
    private final ObjectProvider<ActionUnitListPanel> actionUnitListPanelProvider;
    private final ObjectProvider<ContainerListPanel> containerListPanelProvider;
    private final ObjectProvider<RecordingUnitListPanel> recordingUnitListPanelProvider;
    private final ObjectProvider<SpecimenListPanel> specimenListPanelProvider;
    private final ObjectProvider<PhaseListPanel> phaseListPanelProvider;
    private final ObjectProvider<DocumentListPanel> documentListPanelProvider;
    private final ObjectProvider<StudyListPanel> studyListPanelProvider;
    private final ObjectProvider<SpatialUnitPanel> spatialUnitPanelProvider;
    private final ObjectProvider<ActionUnitPanel> actionUnitPanelProvider;
    private final ObjectProvider<RecordingUnitPanel> recordingUnitPanelProvider;
    private final ObjectProvider<SpecimenPanel> specimenPanelProvider;
    private final ObjectProvider<ContainerPanel> containerPanelProvider;
    private final ObjectProvider<PhasePanel> phasePanelProvider;
    private final ObjectProvider<DocumentPanel> documentPanelProvider;
    private final ObjectProvider<StudyPanel> studyPanelProvider;
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
            case CONTAINER -> id == null ? containerListPanelProvider.getObject() : entity(containerPanelProvider, id);
            case PHASE -> id == null ? phaseListPanelProvider.getObject() : entity(phasePanelProvider, id);
            case DOCUMENT -> id == null ? documentListPanelProvider.getObject() : entity(documentPanelProvider, id);
            case STUDY -> id == null ? studyListPanelProvider.getObject() : entity(studyPanelProvider, id);
            case "welcome" -> welcomePanelProvider.getObject();
            default -> throw new IllegalArgumentException("Unknown panel type: " + type);
        };
    }

    /**
     * The panel of one entity, by the React registry key of its type ("project", "recordingUnit", …),
     * or null for an unknown key.
     */
    public AbstractPanel createForReactEntityType(String entityType, Long id) {
        return switch (entityType) {
            case "project" -> entity(actionUnitPanelProvider, id);
            case "recordingUnit" -> entity(recordingUnitPanelProvider, id);
            case "find" -> entity(specimenPanelProvider, id);
            case PHASE -> entity(phasePanelProvider, id);
            case DOCUMENT -> entity(documentPanelProvider, id);
            case STUDY -> entity(studyPanelProvider, id);
            case CONTAINER -> entity(containerPanelProvider, id);
            case "place" -> entity(spatialUnitPanelProvider, id);
            default -> null;
        };
    }

    /**
     * The resource path of a type's list ("/action-unit"), by the React registry key of the type
     * ("project", "recordingUnit", …), or null for an unknown key: the inverse of {@link #create}'s
     * first segment, for what only knows the key (a saved view).
     */
    public static String listPathOf(String reactEntityType) {
        if (reactEntityType == null) {
            return null;
        }
        return switch (reactEntityType) {
            case "project" -> "/action-unit";
            case "recordingUnit" -> "/recording-unit";
            case "find" -> "/specimen";
            case PHASE -> "/phase";
            case DOCUMENT -> "/document";
            case STUDY -> "/study";
            case CONTAINER -> "/container";
            case "place" -> "/spatial-unit";
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
