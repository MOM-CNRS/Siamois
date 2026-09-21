package fr.siamois.ui.redirection;

import fr.siamois.ui.bean.NavBean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

/**
 * Project's root routes forward straight to /pages/focus.xhtml (plan §7/§8 phase 8), never
 * flow.xhtml — FocusViewBean.beforeInit() decodes the "main" token itself and builds the panel
 * via PanelFactory directly, the same mechanism FlowBean.redirectToDashboard()/
 * SettingsController.goToFocus() already use for Home and Settings. flow.xhtml's own tab-stack
 * rendering has no React branch and no equivalent of focus.xhtml's titlebar, so it must never be
 * the destination for an entity type that has migrated.
 */
@ExtendWith(MockitoExtension.class)
class ActionUnitControllerTest {

    @Mock
    private NavBean navBean;

    private String decodeMainToken(String forward) {
        String token = forward.substring(forward.indexOf("main=") + "main=".length());
        return new String(Base64.getUrlDecoder().decode(token), StandardCharsets.UTF_8);
    }

    @Test
    void toActionUnit_forwardsToFocusXhtml_withTheEntityPathEncoded() {
        ActionUnitController controller = new ActionUnitController(navBean);

        String result = controller.toActionUnit(42L);

        assertThat(result).startsWith("forward:/pages/focus.xhtml?main=");
        assertThat(decodeMainToken(result)).isEqualTo("action-unit/42");
        verify(navBean).setApplicationMode(NavBean.ApplicationMode.SIAMOIS);
    }

    @Test
    void toActionUnitList_forwardsToFocusXhtml_withTheListPathEncoded() {
        ActionUnitController controller = new ActionUnitController(navBean);

        String result = controller.toActionUnitList();

        assertThat(result).startsWith("forward:/pages/focus.xhtml?main=");
        assertThat(decodeMainToken(result)).isEqualTo("action-unit");
        verify(navBean).setApplicationMode(NavBean.ApplicationMode.SIAMOIS);
    }
}
