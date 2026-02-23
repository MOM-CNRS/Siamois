package fr.siamois.ui.bean.panel;


import fr.siamois.ui.bean.panel.models.PanelBreadcrumb;
import fr.siamois.ui.bean.panel.models.panel.WelcomePanel;
import fr.siamois.ui.bean.panel.models.panel.list.ActionUnitListPanel;
import fr.siamois.ui.bean.panel.models.panel.list.RecordingUnitListPanel;
import fr.siamois.ui.bean.panel.models.panel.list.SpatialUnitListPanel;
import fr.siamois.ui.bean.panel.models.panel.list.SpecimenListPanel;
import fr.siamois.ui.bean.panel.models.panel.single.ActionUnitPanel;
import fr.siamois.ui.bean.panel.models.panel.single.RecordingUnitPanel;
import fr.siamois.ui.bean.panel.models.panel.single.SpatialUnitPanel;
import fr.siamois.ui.bean.panel.models.panel.single.SpecimenPanel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import javax.faces.bean.ApplicationScoped;


@Component
@ApplicationScoped
public class PanelFactory {

    private final ObjectProvider<SpatialUnitListPanel> spatialUnitListPanelProvider;
    private final ObjectProvider<ActionUnitListPanel> actionUnitListPanelProvider;
    private final ObjectProvider<SpatialUnitPanel> spatialUnitPanelProvider;

    private final ObjectProvider<ActionUnitPanel> actionUnitPanelProvider;
    private final ObjectProvider<RecordingUnitPanel> recordingUnitPanelProvider;
    private final ObjectProvider<RecordingUnitListPanel> recordingUnitListPanelProvider;
    private final ObjectProvider<SpecimenListPanel> specimenListPanel;
    private final ObjectProvider<WelcomePanel> welcomePanelProvider;
    private final ObjectProvider<SpecimenPanel> specimenPanelProvider;


    public PanelFactory(
            ObjectProvider<SpatialUnitListPanel> spatialUnitListPanelProvider,
            ObjectProvider<ActionUnitListPanel> actionUnitListPanelProvider,
            ObjectProvider<SpatialUnitPanel> spatialUnitPanelProvider,

            ObjectProvider<ActionUnitPanel> actionUnitPanelProvider,
            ObjectProvider<RecordingUnitPanel> recordingUnitPanelProvider,
            ObjectProvider<RecordingUnitListPanel> recordingUnitListPanelProvider, ObjectProvider<SpecimenListPanel> specimenListPanel,
            ObjectProvider<WelcomePanel> welcomePanelProvider, ObjectProvider<SpecimenPanel> specimenPanelProvider) {

        this.spatialUnitListPanelProvider = spatialUnitListPanelProvider;
        this.actionUnitListPanelProvider = actionUnitListPanelProvider;
        this.spatialUnitPanelProvider = spatialUnitPanelProvider;

        this.actionUnitPanelProvider = actionUnitPanelProvider;
        this.recordingUnitPanelProvider = recordingUnitPanelProvider;
        this.recordingUnitListPanelProvider = recordingUnitListPanelProvider;
        this.specimenListPanel = specimenListPanel;
        this.welcomePanelProvider = welcomePanelProvider;
        this.specimenPanelProvider = specimenPanelProvider;
    }

    public SpatialUnitPanel createSpatialUnitPanel(Long spatialUnitId) {

        PanelBreadcrumb bc = new PanelBreadcrumb();

        return new SpatialUnitPanel.SpatialUnitPanelBuilder(spatialUnitPanelProvider)
                .id(spatialUnitId)
                .breadcrumb(bc)
                .build();

    }


    public SpatialUnitPanel createSpatialUnitPanel(Long spatialUnitId, Integer activeIndex) {


        return new SpatialUnitPanel.SpatialUnitPanelBuilder(spatialUnitPanelProvider)
                .id(spatialUnitId)
                .activeIndex(activeIndex)
                .build();
    }


    public ActionUnitPanel createActionUnitPanel(Long actionUnitId,
                                                 Integer activeTabIndex) {


        return new ActionUnitPanel.ActionUnitPanelBuilder(actionUnitPanelProvider)
                .id(actionUnitId)
                .activeIndex(activeTabIndex)
                .build();

    }

    public ActionUnitPanel createActionUnitPanel(Long actionUnitId) {

        return new ActionUnitPanel.ActionUnitPanelBuilder(actionUnitPanelProvider)
                .id(actionUnitId)
                .build();

    }


    public RecordingUnitPanel createRecordingUnitPanel(Long recordingUnitId) {


        return new RecordingUnitPanel.RecordingUnitPanelBuilder(recordingUnitPanelProvider)
                .id(recordingUnitId)
                .build();

    }

    public RecordingUnitPanel createRecordingUnitPanel(Long recordingUnitId, Integer tabIndex) {

        return new RecordingUnitPanel.RecordingUnitPanelBuilder(recordingUnitPanelProvider)
                .id(recordingUnitId)
                .tabIndex(tabIndex)
                .build();

    }


    public SpecimenPanel createSpecimenPanel(Long id) {



        return new SpecimenPanel.Builder(specimenPanelProvider)
                .id(id)

                .build();

    }


    public SpatialUnitListPanel createSpatialUnitListPanel() {
        return new SpatialUnitListPanel.SpatialUnitListPanelBuilder(spatialUnitListPanelProvider)
                .build();
    }

    public ActionUnitListPanel createActionUnitListPanel() {
        return new ActionUnitListPanel.ActionUnitListPanelBuilder(actionUnitListPanelProvider)
                .build();
    }

    public RecordingUnitListPanel createRecordingUnitListPanel() {
        return new RecordingUnitListPanel.RecordingUnitListPanelBuilder(recordingUnitListPanelProvider)
                .build();
    }

    public SpecimenListPanel createSpecimenListPanel() {
        return new SpecimenListPanel.Builder(specimenListPanel)
                .build();
    }

    public WelcomePanel createWelcomePanel() {
        return welcomePanelProvider.getObject();
    }

}