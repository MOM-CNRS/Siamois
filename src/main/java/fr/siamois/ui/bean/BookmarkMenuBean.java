package fr.siamois.ui.bean;


import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.services.BookmarkService;
import fr.siamois.dto.entity.BookmarkDTO;
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
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.Serializable;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@Getter
@Scope(value = "session", proxyMode = ScopedProxyMode.TARGET_CLASS)
@RequiredArgsConstructor
public class BookmarkMenuBean implements Serializable {

    private final FlowBean flowBean;
    private final SessionSettingsBean sessionSettingsBean;
    private final BookmarkService bookmarkService;

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
                Pageable pageable = PageRequest.of(pageIndex, pageSize);

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
        log.info("Saving updated title for Bookmark ID {}: {}", editedItem.getId(), editedItem.getTitle());

        // TODO: Invoke your database layer to save changes permanently:
        // bookmarkService.updateTitle(editedItem.getId(), editedItem.getTitle());


    }

    public void onRowCancel(RowEditEvent<BookmarkDTO> event) {
        BookmarkDTO cancelledItem = event.getObject();
        log.info("Editing cancelled for Bookmark ID: {}", cancelledItem.getId());
        // No database action required here, JSF rolls back the local property changes natively
    }

}