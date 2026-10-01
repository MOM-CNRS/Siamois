package fr.siamois.ui.redirection;

import fr.siamois.ui.bean.NavBean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Every app route forwards to /pages/focus.xhtml, the only page that renders panels: these are
 * the URLs the React main panel pushes with history.pushState, so they are exactly what an F5 hits
 * (/phase + /container previously had no route at all — F5 landed on the 404 page).
 */
@ExtendWith(MockitoExtension.class)
class EntityFocusRoutesTest {

    @Mock
    private NavBean navBean;

    private static String decodedMain(String forward) {
        assertThat(forward).startsWith("forward:/pages/focus.xhtml?main=");
        String token = forward.substring(forward.indexOf("main=") + "main=".length());
        return new String(Base64.getUrlDecoder().decode(token), StandardCharsets.UTF_8);
    }

    @Test
    void recordingUnitRoutes_forwardToFocus() {
        RecordingUnitController controller = new RecordingUnitController(navBean);
        assertThat(decodedMain(controller.toRecordingUnit(12L))).isEqualTo("recording-unit/12");
        assertThat(decodedMain(controller.toRecordingUnitList())).isEqualTo("recording-unit");
        verify(navBean, times(2)).setApplicationMode(NavBean.ApplicationMode.SIAMOIS);
    }

    @Test
    void specimenRoutes_forwardToFocus() {
        SpecimenController controller = new SpecimenController(navBean);
        assertThat(decodedMain(controller.toSpecimen(3L))).isEqualTo("specimen/3");
        assertThat(decodedMain(controller.toSpecimenList())).isEqualTo("specimen");
    }

    @Test
    void spatialUnitRoutes_forwardToFocus() {
        SpatialUnitController controller = new SpatialUnitController(navBean);
        assertThat(decodedMain(controller.toSpatialUnit(4L))).isEqualTo("spatial-unit/4");
        assertThat(decodedMain(controller.toSpatialUnitList())).isEqualTo("spatial-unit");
    }

    @Test
    void phaseRoutes_forwardToFocus() {
        PhaseController controller = new PhaseController(navBean);
        assertThat(decodedMain(controller.toPhase(5L))).isEqualTo("phase/5");
        assertThat(decodedMain(controller.toPhaseList())).isEqualTo("phase");
    }

    @Test
    void containerRoutes_forwardToFocus() {
        ContainerController controller = new ContainerController(navBean);
        assertThat(decodedMain(controller.toContainer(6L))).isEqualTo("container/6");
        assertThat(decodedMain(controller.toContainerList())).isEqualTo("container");
    }

    @Test
    void welcomeAndDashboard_forwardToFocusHome() {
        WelcomeController controller = new WelcomeController(navBean);
        assertThat(decodedMain(controller.toWelcome())).isEqualTo("welcome");
    }

    // The two "new" routes used to show flow.xhtml; creation happens in the new-unit dialog, so
    // they now land on the parent entity instead.
    @Test
    void newUnitRoutes_forwardToTheParentEntity() {
        assertThat(decodedMain(new RecordingUnitController(navBean).newRecordingUnit(8L))).isEqualTo("action-unit/8");
        assertThat(decodedMain(new ActionUnitController(navBean).addActionUnit(9L))).isEqualTo("spatial-unit/9");
    }
}
