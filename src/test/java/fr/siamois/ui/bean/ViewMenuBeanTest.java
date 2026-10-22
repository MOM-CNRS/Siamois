package fr.siamois.ui.bean;

import fr.siamois.domain.models.UserInfo;
import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.dto.view.TableViewState;
import fr.siamois.ui.api.openapi.v1.resource.view.ViewListResponse;
import fr.siamois.ui.api.openapi.v1.resource.view.ViewResource;
import fr.siamois.ui.api.openapi.v1.service.ViewOpenApiService;
import fr.siamois.ui.bean.panel.FlowBean;
import fr.siamois.ui.bean.panel.PanelFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

class ViewMenuBeanTest {

    private final FlowBean flowBean = mock(FlowBean.class);
    private final SessionSettingsBean sessionSettingsBean = mock(SessionSettingsBean.class);
    private final LangBean langBean = mock(LangBean.class);
    private final ViewOpenApiService viewService = mock(ViewOpenApiService.class);
    private final UserInfo user = new UserInfo(new InstitutionDTO(), new PersonDTO(), "fr");
    private ViewMenuBean bean;

    @BeforeEach
    void setUp() {
        bean = new ViewMenuBean(flowBean, sessionSettingsBean, langBean, viewService);
        bean.init();
        when(sessionSettingsBean.getUserInfo()).thenReturn(user);
    }

    private static ViewResource view(long id, String type, String title, boolean canEdit) {
        return new ViewResource(id, type, title, "USER", null, null, null, new TableViewState(), 7L, "Ada Lovelace", canEdit, null, null);
    }

    @Test
    void listsTheOrganisationsViewsOfEveryType_narrowedByTheSearch() {
        bean.setSearch("fosse");
        when(viewService.list(user, null, "fosse", 40, 0)).thenReturn(new ViewListResponse(
                List.of(view(12, "recordingUnit", "Mes UE", true), view(13, "nope", "Inconnue", false)), 2, 40, 0));

        List<ViewMenuBean.ViewMenuRow> rows = bean.getLazyModel().load(0, 40, Map.of(), Map.of());

        assertThat(rows).extracting(ViewMenuBean.ViewMenuRow::getTitle).containsExactly("Mes UE", "Inconnue");
        assertThat(bean.getLazyModel().getRowCount()).isEqualTo(2);
        assertThat(rows.get(0).openUri()).isEqualTo("/recording-unit?viewId=12");
        assertThat(rows.get(0).getIcon()).isEqualTo("bi bi-pencil-square");
        // A type the server can't open is listed but can't be opened.
        assertThat(rows.get(1).getListUri()).isNull();
        assertThat(rows.get(1).openUri()).isNull();
    }

    @Test
    void opensAViewOnItsList() throws IOException {
        when(viewService.list(any(), isNull(), any(), anyInt(), anyInt())).thenReturn(
                new ViewListResponse(List.of(view(12, "project", "Fouilles", false)), 1, 40, 0));
        ViewMenuBean.ViewMenuRow row = bean.getLazyModel().load(0, 40, Map.of(), Map.of()).get(0);

        bean.redirectToEntry(row);

        verify(flowBean).redirectToFocus("/action-unit?viewId=12", null);
    }

    @Test
    void doesNotRedirectForATypeItCannotOpen() throws IOException {
        when(viewService.list(any(), isNull(), any(), anyInt(), anyInt())).thenReturn(
                new ViewListResponse(List.of(view(13, "nope", "Inconnue", false)), 1, 40, 0));

        bean.redirectToEntry(bean.getLazyModel().load(0, 40, Map.of(), Map.of()).get(0));

        verifyNoInteractions(flowBean);
    }

    @Test
    void deletesOnlyAViewOfTheUsersOwn() {
        when(viewService.list(any(), isNull(), any(), anyInt(), anyInt())).thenReturn(new ViewListResponse(
                List.of(view(12, "project", "Mienne", true), view(13, "project", "Autre", false)), 2, 40, 0));
        List<ViewMenuBean.ViewMenuRow> rows = bean.getLazyModel().load(0, 40, Map.of(), Map.of());

        bean.deleteItem(rows.get(1));
        verify(viewService, never()).delete(any(), anyLong());

        bean.deleteItem(rows.get(0));
        verify(viewService).delete(user, 12L);
    }

    @Test
    void aRefusedDeleteLeavesTheViewListed() {
        when(viewService.list(any(), isNull(), any(), anyInt(), anyInt())).thenReturn(
                new ViewListResponse(List.of(view(12, "project", "Mienne", true)), 1, 40, 0));
        ViewMenuBean.ViewMenuRow row = bean.getLazyModel().load(0, 40, Map.of(), Map.of()).get(0);
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "no")).when(viewService).delete(any(), eq(12L));

        bean.deleteItem(row);

        verify(viewService).delete(user, 12L);
    }

    @Test
    void mapsEveryRegistryKeyToItsListPath() {
        assertThat(PanelFactory.listPathOf("project")).isEqualTo("/action-unit");
        assertThat(PanelFactory.listPathOf("recordingUnit")).isEqualTo("/recording-unit");
        assertThat(PanelFactory.listPathOf("find")).isEqualTo("/specimen");
        assertThat(PanelFactory.listPathOf("place")).isEqualTo("/spatial-unit");
        assertThat(PanelFactory.listPathOf("phase")).isEqualTo("/phase");
        assertThat(PanelFactory.listPathOf("document")).isEqualTo("/document");
        assertThat(PanelFactory.listPathOf("study")).isEqualTo("/study");
        assertThat(PanelFactory.listPathOf("container")).isEqualTo("/container");
        assertThat(PanelFactory.listPathOf("nope")).isNull();
        assertThat(PanelFactory.listPathOf(null)).isNull();
    }
}
