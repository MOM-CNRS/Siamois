package fr.siamois.ui.bean.panel.models.panel;

import fr.siamois.domain.services.BookmarkService;
import fr.siamois.ui.bean.LangBean;
import fr.siamois.ui.bean.SessionSettingsBean;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AbstractPanelTest {

    private static ApplicationContext context() {
        ApplicationContext context = mock(ApplicationContext.class);
        when(context.getBean(SessionSettingsBean.class)).thenReturn(mock(SessionSettingsBean.class));
        when(context.getBean(LangBean.class)).thenReturn(mock(LangBean.class));
        when(context.getBean(BookmarkService.class)).thenReturn(mock(BookmarkService.class));
        return context;
    }

    private static final class TestPanel extends AbstractPanel {
        private final String uri;

        TestPanel(String uri) {
            super("title", "bi bi-house", "siamois-panel", context());
            this.uri = uri;
        }

        @Override
        public String ressourceUri() {
            return uri;
        }

        @Override
        public String getPrefixPanelIndex() {
            return "action-unit-list";
        }

        @Override
        public String svgIcon() {
            return "/resources/img/svg/house.svg";
        }

        @Override
        public String reactPanelKind() {
            return "list";
        }
    }

    @Test
    void panelIndex_isThePrefixForARootPanel_andSuffixedForAnOverview() {
        TestPanel panel = new TestPanel("/action-unit");
        assertThat(panel.getPanelIndex()).isEqualTo("action-unit-list");

        panel.setRoot(false);
        assertThat(panel.getPanelIndex()).isEqualTo("action-unit-list-overview");
    }

    @Test
    void jsPanelIndex_isAValidJsIdentifier() {
        TestPanel panel = new TestPanel("/action-unit");
        panel.setRoot(false);
        assertThat(panel.getJsPanelIndex()).isEqualTo("action_unit_list_overview");
    }

    @Test
    void closeOverview_forgetsTheOverview() {
        TestPanel panel = new TestPanel("/action-unit");
        panel.setParentOrOverview(new TestPanel("/action-unit/1"));

        panel.closeOverview();

        assertThat(panel.getParentOrOverview()).isNull();
    }

    @Test
    void panelsAreEqualByResourceUri() {
        assertThat(new TestPanel("/action-unit/1")).isEqualTo(new TestPanel("/action-unit/1"))
                .isNotEqualTo(new TestPanel("/action-unit/2"));
    }
}
