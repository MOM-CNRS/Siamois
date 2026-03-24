package fr.siamois.ui.bean;


import fr.siamois.ui.bean.panel.FlowBean;
import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/*
Bean managing the history of a session
 */
@Slf4j
@Component
@Getter
@Setter
@Scope(value = "session", proxyMode = ScopedProxyMode.TARGET_CLASS)
@RequiredArgsConstructor
public class HistoryBean {

    private final FlowBean flowBean;

    private List<HistoryItem> items = new ArrayList<>() ;

    public void redirectToEntry(HistoryItem entry) throws IOException {

        flowBean.redirectToFocus(entry.main.uri, entry.secondary != null ? entry.secondary.uri :  null);

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
