package fr.siamois.ui.bean;

import fr.siamois.domain.models.UserInfo;
import fr.siamois.dto.entity.BookmarkDTO;
import fr.siamois.ui.api.openapi.v1.resource.view.ViewListResponse;
import fr.siamois.ui.api.openapi.v1.resource.view.ViewResource;
import fr.siamois.ui.api.openapi.v1.service.ViewOpenApiService;
import fr.siamois.ui.bean.panel.FlowBean;
import fr.siamois.ui.bean.panel.PanelFactory;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.primefaces.model.FilterMeta;
import org.primefaces.model.LazyDataModel;
import org.primefaces.model.SortMeta;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * The sidebar's « Vues » drawer: the saved list views of the organisation, visible to all its members,
 * most recently changed first, narrowed by a title search. A view opens as its list's URI plus
 * {@code ?viewId=}, which the React list reads. Only a view's owner deletes it.
 */
@Slf4j
@Component
@Getter
@Scope(value = "session", proxyMode = ScopedProxyMode.TARGET_CLASS)
@RequiredArgsConstructor
public class ViewMenuBean implements Serializable {

    /** One line of the drawer. */
    @Getter
    public static final class ViewMenuRow implements Serializable {
        private final Long id;
        private final String title;
        private final String ownerName;
        /** The list's resource URI without the view ("/recording-unit"); null for a type the server can't open. */
        private final String listUri;
        private final String icon;
        private final String color;
        private final boolean canEdit;

        ViewMenuRow(ViewResource view) {
            this.id = view.id();
            this.title = view.title();
            this.ownerName = view.ownerName();
            this.listUri = PanelFactory.listPathOf(view.resourceType());
            BookmarkDTO look = new BookmarkDTO();
            look.setResourceUri(listUri);
            // The bookmark icon of "no URI" is a generic one: fine for a type the drawer doesn't know.
            this.icon = listUri == null ? "bi bi-file-earmark-spreadsheet" : look.getBookmarkIcon();
            this.color = look.getBookmarkColor();
            this.canEdit = view.canEdit();
        }

        /** The URI that opens the view: the list's, on this view. */
        public String openUri() {
            return listUri == null ? null : listUri + "?viewId=" + id;
        }
    }

    private final FlowBean flowBean;
    private final SessionSettingsBean sessionSettingsBean;
    private final LangBean langBean;
    private final transient ViewOpenApiService viewService;

    /** What the title search holds; empty lists every view. */
    @Setter
    private String search;

    private LazyDataModel<ViewMenuRow> lazyModel;

    @PostConstruct
    public void init() {
        this.lazyModel = new LazyDataModel<>() {
            private static final long serialVersionUID = 1L;

            @Override
            public String getRowKey(ViewMenuRow row) {
                return String.valueOf(row.getId());
            }

            @Override
            public int count(Map<String, FilterMeta> filterBy) {
                return getRowCount();
            }

            @Override
            public List<ViewMenuRow> load(int first, int pageSize, Map<String, SortMeta> sortBy, Map<String, FilterMeta> filterBy) {
                UserInfo user = sessionSettingsBean.getUserInfo();
                ViewListResponse page = viewService.list(user, null, search, pageSize, first);
                setRowCount((int) page.totalCount());
                return page.data().stream().map(ViewMenuRow::new).toList();
            }
        };
    }

    public void redirectToEntry(ViewMenuRow row) throws IOException {
        if (row != null && row.openUri() != null) {
            flowBean.redirectToFocus(row.openUri(), null);
        }
    }

    /** Deletes a view of the user's own; the service refuses anyone else's, which just leaves it listed. */
    public void deleteItem(ViewMenuRow row) {
        if (row == null || !row.isCanEdit()) {
            return;
        }
        try {
            viewService.delete(sessionSettingsBean.getUserInfo(), row.getId());
        } catch (ResponseStatusException e) {
            log.warn("View {} not deleted: {}", row.getId(), e.getReason());
        }
    }

    /** The line under a view's title: who made it. */
    public String tooltipOf(ViewMenuRow row) {
        return row.getOwnerName() == null ? row.getTitle() : langBean.msg("views.sidebar.by", row.getOwnerName());
    }
}
