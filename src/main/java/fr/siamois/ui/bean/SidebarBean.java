package fr.siamois.ui.bean;

import lombok.Getter;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.stereotype.Component;
import java.io.Serializable;

@Component
@Getter
@Scope(value = "session", proxyMode = ScopedProxyMode.TARGET_CLASS)
public class SidebarBean implements Serializable {

    public enum SidebarMode {
        NONE, HISTORIQUE, BOOKMARKS
    }

    private SidebarMode currentMode = SidebarMode.NONE;

    // Toggle logic for the sidebar UI
    public void toggleMode(String modeStr) {
        SidebarMode targetMode = SidebarMode.valueOf(modeStr.toUpperCase());
        if (this.currentMode == targetMode) {
            this.currentMode = SidebarMode.NONE;
        } else {
            this.currentMode = targetMode;
        }
    }

    public void close() {
        this.currentMode = SidebarMode.NONE;
    }

    public boolean isVisible() {
        return this.currentMode != SidebarMode.NONE;
    }
}