package fr.siamois.ui.bean;

import fr.siamois.domain.events.publisher.InstitutionChangeEventPublisher;
import fr.siamois.domain.events.publisher.LoginEventPublisher;
import fr.siamois.domain.services.ResourceInstitutionService;
import fr.siamois.domain.services.permissions.ProfilePermissionService;
import fr.siamois.ui.bean.panel.PanelFactory;
import fr.siamois.ui.bean.panel.models.panel.AbstractPanel;
import jakarta.faces.context.ExternalContext;
import jakarta.faces.context.FacesContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * FocusViewBean#setMainFromRequest: the React main pane navigated client-side, and the view's main
 * panel (history sidebar, pairing of the next overview) follows it.
 */
class FocusViewBeanSetMainTest {

    private final PanelFactory panelFactory = mock(PanelFactory.class);
    private final ResourceInstitutionService resourceInstitutionService = mock(ResourceInstitutionService.class);
    private final HistoryBean historyBean = new HistoryBean(null);
    private final Map<String, String> params = new HashMap<>();
    private MockedStatic<FacesContext> facesContextStatic;
    private FocusViewBean bean;

    @BeforeEach
    void setUp() {
        bean = new FocusViewBean(panelFactory, historyBean, mock(LangBean.class), mock(SessionSettingsBean.class),
                resourceInstitutionService, mock(ProfilePermissionService.class),
                mock(InstitutionChangeEventPublisher.class), mock(LoginEventPublisher.class), mock(RedirectBean.class));
        // A list or an entity of the active organisation: no switch, no permission check to make.
        when(resourceInstitutionService.findInstitutionOf(any(), any())).thenReturn(Optional.empty());

        FacesContext facesContext = mock(FacesContext.class);
        ExternalContext externalContext = mock(ExternalContext.class);
        when(facesContext.getExternalContext()).thenReturn(externalContext);
        when(externalContext.getRequestParameterMap()).thenReturn(params);
        facesContextStatic = mockStatic(FacesContext.class);
        facesContextStatic.when(FacesContext::getCurrentInstance).thenReturn(facesContext);
    }

    @AfterEach
    void tearDown() {
        facesContextStatic.close();
    }

    private static AbstractPanel panel(String title, String uri) {
        AbstractPanel panel = mock(AbstractPanel.class);
        when(panel.resolveTitleOrTitleCode()).thenReturn(title);
        when(panel.ressourceUri()).thenReturn(uri);
        return panel;
    }

    @Test
    void movesTheMainPanelAndRecordsItInTheHistory() {
        AbstractPanel previous = panel("Projets", "/action-unit");
        bean.setMainPanel(previous);
        AbstractPanel next = panel("UE-12", "/recording-unit/12");
        when(panelFactory.create("recording-unit", 12L)).thenReturn(next);
        params.put("path", "/recording-unit/12");

        bean.setMainFromRequest();

        assertThat(bean.getMainPanel()).isSameAs(next);
        verify(next).setRoot(true);
        assertThat(historyBean.getItems()).hasSize(1);
        assertThat(historyBean.getItems().get(0).getMain().getUri()).isEqualTo("/recording-unit/12");
        assertThat(historyBean.getItems().get(0).getSecondary()).isNull();
    }

    @Test
    void keepsTheOpenOverviewAttachedToTheNewMainPanel() {
        AbstractPanel previous = panel("Projets", "/action-unit");
        AbstractPanel overview = panel("Fouille A", "/action-unit/3");
        when(previous.getParentOrOverview()).thenReturn(overview);
        bean.setMainPanel(previous);
        AbstractPanel next = panel("UE", "/recording-unit");
        when(panelFactory.create("recording-unit", null)).thenReturn(next);
        params.put("path", "/recording-unit");

        bean.setMainFromRequest();

        verify(next).setParentOrOverview(overview);
        verify(overview).setParentOrOverview(next);
        assertThat(historyBean.getItems().get(0).getSecondary().getUri()).isEqualTo("/action-unit/3");
    }

    @Test
    void ignoresAPathTheServerDoesNotKnow() {
        AbstractPanel previous = panel("Projets", "/action-unit");
        bean.setMainPanel(previous);
        when(panelFactory.create(eq("nope"), any())).thenThrow(new IllegalArgumentException("Unknown panel type: nope"));
        params.put("path", "/nope/1");

        bean.setMainFromRequest();

        assertThat(bean.getMainPanel()).isSameAs(previous);
        assertThat(historyBean.getItems()).isEmpty();
        verify(panelFactory, never()).create(eq("recording-unit"), anyLong());
    }

    @Test
    void doesNothingWithoutAPath() {
        AbstractPanel previous = panel("Projets", "/action-unit");
        bean.setMainPanel(previous);

        bean.setMainFromRequest();

        assertThat(bean.getMainPanel()).isSameAs(previous);
        verifyNoInteractions(panelFactory);
    }
}
