package fr.siamois.ui.bean;


import fr.siamois.domain.models.UserHistoryEntry;
import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.services.UserHistoryService;
import fr.siamois.ui.bean.panel.FlowBean;
import lombok.Data;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/*
Bean managing the history shown in the sidebar: the pages the user opened lately in the active organisation.
They are kept in base (UserHistoryService), so they outlive the session; the session holds the list to show,
loaded from there on first use.
 */
@Slf4j
@Component
@Getter
@Setter
@Scope(value = "session", proxyMode = ScopedProxyMode.TARGET_CLASS)
@RequiredArgsConstructor
public class HistoryBean {

    private static final int MAX_ITEMS = 20;

    private final FlowBean flowBean;
    private final transient UserHistoryService historyService;
    // Looked up when needed: SessionSettingsBean depends on FlowBean, which depends on this bean.
    private final transient ObjectProvider<SessionSettingsBean> sessionSettings;

    private List<HistoryItem> items = new ArrayList<>();
    private boolean loaded;

    /** The pages to show: what the session recorded, after what an earlier session left in base (read once). */
    public List<HistoryItem> getItems() {
        if (!loaded) {
            loaded = true;
            List<HistoryItem> stored = loadStored();
            stored.addAll(items);
            items = stored;
        }
        return items;
    }

    /** The active organisation changed: its history, not the previous one's, is shown next. */
    public void reset() {
        items = new ArrayList<>();
        loaded = false;
    }

    private List<HistoryItem> loadStored() {
        UserInfo userInfo = currentUserInfo();
        List<HistoryItem> stored = new ArrayList<>();
        if (userInfo == null) {
            return stored;
        }
        try {
            for (UserHistoryEntry entry : historyService.latest(userInfo, MAX_ITEMS)) {
                stored.add(toItem(entry));
            }
        } catch (RuntimeException e) {
            log.warn("History could not be loaded", e);
        }
        return stored;
    }

    private UserInfo currentUserInfo() {
        if (historyService == null || sessionSettings == null) {
            return null;
        }
        SessionSettingsBean bean = sessionSettings.getIfAvailable();
        return bean == null ? null : bean.getUserInfo();
    }

    private static HistoryItem toItem(UserHistoryEntry entry) {
        HistoryItem item = new HistoryItem();
        item.setMain(component(entry.getMainUri(), entry.getMainTitle(), entry.getMainIcon(), entry.getMainStyleClass()));
        if (!entry.getSecondaryUri().isEmpty()) {
            item.setSecondary(component(entry.getSecondaryUri(), entry.getSecondaryTitle(),
                    entry.getSecondaryIcon(), entry.getSecondaryStyleClass()));
        }
        return item;
    }

    private static HistoryItemComponent component(String uri, String title, String icon, String styleClass) {
        HistoryItemComponent component = new HistoryItemComponent();
        component.setUri(uri);
        component.setTitle(title);
        component.setIcon(icon);
        component.setStyleClass(styleClass);
        return component;
    }

    private void persist(HistoryItem item) {
        UserInfo userInfo = currentUserInfo();
        if (userInfo == null) {
            return;
        }
        HistoryItemComponent main = item.getMain();
        HistoryItemComponent side = item.getSecondary();
        try {
            historyService.record(userInfo, new UserHistoryService.Visit(
                    main.getUri(), main.getTitle(), main.getIcon(), main.getStyleClass(),
                    side == null ? null : side.getUri(), side == null ? null : side.getTitle(),
                    side == null ? null : side.getIcon(), side == null ? null : side.getStyleClass()));
        } catch (RuntimeException e) {
            // Keeping the history is never worth failing a navigation.
            log.warn("History entry could not be saved", e);
        }
    }

    public void redirectToEntry(HistoryItem entry) throws IOException {

        flowBean.redirectToFocus(entry.main.uri, entry.secondary != null ? entry.secondary.uri :  null);

    }


    public void addItem(HistoryItem item) {

        if(item.getMain() == null) {
            return;

        }
        getItems();
        persist(item);

        // Remove any existing item with the same main URI and secondary URI
        items.removeIf(existingItem ->
                existingItem.getMain().getUri().equals(item.getMain().getUri()) &&
                        ((existingItem.getSecondary() == null && item.getSecondary() == null) ||
                                (existingItem.getSecondary() != null && item.getSecondary() != null &&
                                        existingItem.getSecondary().getUri().equals(item.getSecondary().getUri())))
        );

        // Add the new item
        items.add(0, item); // Add to the beginning to keep the most recent first

        // Enforce max size
        if (items.size() > MAX_ITEMS) {
            items = new ArrayList<>(items.subList(0, MAX_ITEMS));
        }
    }


    @Data
    public static class HistoryItem {
        private HistoryItemComponent main;
        private HistoryItemComponent secondary;

        public String getTitle() {
            if(secondary != null) {
                return secondary.getTitle();
            }
            else {
                return main.getTitle();
            }
        }

        public String getIcon() {
            if(secondary != null) {
                return secondary.getIcon();
            }
            else {
                return main.getIcon();
            }
        }

        public String getStyleClass() {
            if(secondary != null) {
                return secondary.getStyleClass();
            }
            else {
                return main.getStyleClass();
            }
        }


    }

    @Data
    public static class HistoryItemComponent {
        private String title;
        private String uri;
        private String icon;
        private String styleClass;
    }

}
