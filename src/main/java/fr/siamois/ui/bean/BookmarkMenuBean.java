package fr.siamois.ui.bean;


import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.primefaces.model.LazyDataModel;
import org.primefaces.model.FilterMeta;
import org.primefaces.model.SortMeta;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
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

    private final fr.siamois.ui.bean.panel.FlowBean flowBean;

    // PrimeFaces Lazy Model
    private LazyDataModel<BookmarkItem> lazyModel;

    @PostConstruct
    public void init() {
        this.lazyModel = new LazyDataModel<>() {
            private static final long serialVersionUID = 1L;

            @Override
            public int count(Map<String, FilterMeta> filterBy) {
                // TODO: Replace with your actual database/service count query
                return 100;
            }

            @Override
            public List<BookmarkItem> load(int first, int pageSize, Map<String, SortMeta> sortBy, Map<String, FilterMeta> filterBy) {
                log.info("Lazy loading bookmarks: first={}, pageSize={}", first, pageSize);

                // TODO: Replace with database pagination query using 'first' and 'pageSize'
                // e.g., return bookmarkService.findPaginated(first, pageSize);
                return List.of(
                        new BookmarkItem("1", "Pôle Gare - Phase 1", "/pole-gare", "bi bi-folder-fill", "text-primary"),
                        new BookmarkItem("2", "Tr10 Subdivision", "/tr10", "bi bi-file-earmark-text", "")
                );
            }
        };
    }

    public void redirectToEntry(BookmarkItem item) throws IOException {
        if (item != null && item.getUri() != null) {
            flowBean.redirectToFocus(item.getUri(), null);
        }
    }

    public void deleteItem(BookmarkItem item) {
        log.info("Deleting bookmark ID: {}", item.getId());
        // TODO: Call your service layers to delete from DB: bookmarkService.delete(item.getId());

        // No manual list manipulation needed for dynamic models!
        // Simply delete from the database and refresh the PrimeFaces component grid.
    }
}