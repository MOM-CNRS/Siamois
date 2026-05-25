package fr.siamois.ui.bean;


import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.models.uiview.UiTableView;
import fr.siamois.domain.services.BookmarkService;
import fr.siamois.domain.services.UiViewService;
import fr.siamois.dto.entity.BookmarkDTO;
import fr.siamois.dto.view.UITableViewDTO;
import fr.siamois.ui.bean.panel.FlowBean;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.primefaces.event.RowEditEvent;
import org.primefaces.model.LazyDataModel;
import org.primefaces.model.FilterMeta;
import org.primefaces.model.SortMeta;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Component
@Getter
@Scope(value = "session", proxyMode = ScopedProxyMode.TARGET_CLASS)
@RequiredArgsConstructor
public class BookmarkMenuBean implements Serializable {

    private final FlowBean flowBean;
    private final SessionSettingsBean sessionSettingsBean;
    private final BookmarkService bookmarkService;
    private final UiViewService uiViewService;
    private final NavBean navBean;

    // PrimeFaces Lazy Model
    private LazyDataModel<BookmarkDTO> lazyModel;

    @PostConstruct
    public void init() {
        this.lazyModel = new LazyDataModel<BookmarkDTO>() {
            private static final long serialVersionUID = 1L;

            // Cache object to temporarily store the active page metadata across load cycles
            private int cachedTotalRows = 0;

            @Override
            public String getRowKey(BookmarkDTO item) {
                return String.valueOf(item.getId());
            }

            @Override
            public int count(Map<String, FilterMeta> filterBy) {
                // PrimeFaces mandates tracking the count. We pass back the value collected during the load execution.
                return this.cachedTotalRows;
            }

            @Override
            public List<BookmarkDTO> load(int first, int pageSize, Map<String, SortMeta> sortBy, Map<String, FilterMeta> filterBy) {
                // Calculate page index (Spring Pages are 0-indexed: page = offset / size)
                int pageIndex = first / pageSize;
                Sort sort = Sort.by(Sort.Direction.DESC, "createdAt");
                Pageable pageable = PageRequest.of(pageIndex, pageSize, sort);

                // Fetch user identity data context details (assumed injected/retrieved)
                UserInfo currentUser = sessionSettingsBean.getUserInfo();

                // Run the single combined transaction request via service layer
                Page<BookmarkDTO> resultPage = bookmarkService.findAll(currentUser, pageable);

                // Store total row elements internally for the secondary count step
                this.cachedTotalRows = (int) resultPage.getTotalElements();

                // Return the localized raw collection list back to the virtual scroll datatable renderer
                return resultPage.getContent();
            }
        };
    }

    public void redirectToEntry(BookmarkDTO item) throws IOException {
        if (item != null && item.getResourceUri() != null) {
            flowBean.redirectToFocus(item.getResourceUri(), null);
        }
    }

    public void deleteItem(BookmarkDTO item) {
        log.trace("Deleting bookmark ID: {}", item.getId());
        bookmarkService.delete(sessionSettingsBean.getUserInfo(), item.getResourceUri());

        // No manual list manipulation needed for dynamic models!
        // Simply delete from the database and refresh the PrimeFaces component grid.
    }

    public void onRowEdit(RowEditEvent<BookmarkDTO> event) {
        BookmarkDTO editedItem = event.getObject();
        Long viewId = resolveViewId(editedItem.getResourceUri());
        if(viewId == null) { return ;}
        // If view exist, get it
        UITableViewDTO view = uiViewService.findOne(viewId);
        if(view == null) { return ;}
        view.setTitle(editedItem.getTitle());
        uiViewService.save(view);
    }

    public String resolveName(BookmarkDTO bookmark) {

        Long viewId = resolveViewId(bookmark.getResourceUri());

        if (viewId == null) {
            return navBean.bookmarkTitle(bookmark);
        }

        UITableViewDTO view = uiViewService.findOne(viewId);

        if (view != null
                && Objects.equals(
                view.getOwner().getId(),
                sessionSettingsBean.getUserInfo().getUser().getId()
        )
                && view.getTitle() != null
                && !view.getTitle().isBlank()) {

            return view.getTitle();
        }

        return navBean.bookmarkTitle(bookmark);
    }

    public void onRowCancel(RowEditEvent<BookmarkDTO> event) {
        BookmarkDTO cancelledItem = event.getObject();
        log.info("Editing cancelled for Bookmark ID: {}", cancelledItem.getId());
        // No database action required here, JSF rolls back the local property changes natively
    }

    public Boolean canEditViewNameOfBookmark(BookmarkDTO bookmark) {
        Long viewId = resolveViewId(bookmark.getResourceUri());
        if(viewId == null) { return false ;}
        // If view exist, get it and check author
        UITableViewDTO view = uiViewService.findOne(viewId);
        // Can edit if it's the same user
        return view != null && Objects.equals(view.getOwner().getId(), sessionSettingsBean.getUserInfo().getUser().getId());
    }

    private Long resolveViewId(String path) {

        if (path == null || path.isBlank()) {
            return null;
        }

        String[] pathAndQuery = path.split("\\?", 2);

        if (pathAndQuery.length < 2) {
            return null;
        }

        String query = pathAndQuery[1];

        for (String param : query.split("&")) {

            String[] kv = param.split("=", 2);

            if (kv.length == 2 && "viewId".equals(kv[0])) {
                return Long.parseLong(kv[1]);
            }
        }

        return null;
    }
}