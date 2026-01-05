package fr.siamois.ui.bean.panel.models.panel.single.tab;

import lombok.Data;

import java.util.Map;
import java.util.Objects;

@Data
public abstract class PanelTab {

    private String titleCode;
    private String icon;
    private String id;
    private static final String ROOT = "singlePanelUnitForm:singlePanelUnitTabs";

    public String getRoot() {
        return ROOT;
    }

    public PanelTab(String titleCode, String icon, String id) {
        this.titleCode = titleCode;
        this.icon = icon;
        this.id = id;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PanelTab that)) return false;

        return Objects.equals(titleCode, that.titleCode) &&
                Objects.equals(icon, that.icon);
    }

    @Override
    public int hashCode() {
        return Objects.hash(titleCode, icon);
    }

    // Get the xhtml file name
    public abstract String getViewName();

}
